package wonderland.driving.license.test.finder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

@SpringBootApplication
public class TestFinderApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestFinderApplication.class);
    private WebClient testFinder;
    private WebClient telegramBotClient;

    @Autowired
    @Qualifier("notLoadBalancedClient")
    WebClient.Builder notLoadBalancedWebClientBuilder;

    public static void main(String[] args) {
        SpringApplication.run(TestFinderApplication.class, args);
    }

    public static final String Find_English_Theory_Exams_Request_Body = """
              {
              "bookingSession": {
                "socialSecurityNumber": "%s",
                "licenceId": 5,
                "bookingModeId": 0,
                "ignoreDebt": false,
                "ignoreBookingHindrance": false,
                "examinationTypeId": 0,
                "excludeExaminationCategories": [],
                "rescheduleTypeId": 0,
                "paymentIsActive": false,
                "paymentReference": null,
                "paymentUrl": null,
                "searchedMonths": 0
              },
              "occasionBundleQuery": {
                "startDate": "1970-01-01T00:00:00.000Z",
                "searchedMonths": 0,
                "locationId": 1000140,
                "nearbyLocationIds": [1000071],
                "languageId": 4,
                "tachographTypeId": 1,
                "occasionChoiceId": 1,
                "examinationTypeId": 3
              }
            }
            """;

    public static final String Find_PERSIAN_Theory_Exams_IN_UPPSALA_Request_Body = """
              {
              "bookingSession": {
                "socialSecurityNumber": "%s",
                "licenceId": 5,
                "bookingModeId": 0,
                "ignoreDebt": false,
                "ignoreBookingHindrance": false,
                "examinationTypeId": 0,
                "excludeExaminationCategories": [],
                "rescheduleTypeId": 0,
                "paymentIsActive": false,
                "paymentReference": null,
                "paymentUrl": null,
                "searchedMonths": 0
              },
              "occasionBundleQuery": {
                "startDate": "1970-01-01T00:00:00.000Z",
                "searchedMonths": 0,
                "locationId": 1000071,
                "nearbyLocationIds": [1000071],
                "languageId": 7,
                "tachographTypeId": 1,
                "occasionChoiceId": 1,
                "examinationTypeId": 3
              }
            }
            """;

    public static final String FIND_MANUAL_PRACTICAL_EXAMS_REQUEST_BODY = """
                {
                  "bookingSession": {
                    "socialSecurityNumber": "%s",
                    "licenceId": 5,
                    "bookingModeId": 0,
                    "ignoreDebt": false,
                    "ignoreBookingHindrance": false,
                    "examinationTypeId": 0,
                    "excludeExaminationCategories": [],
                    "rescheduleTypeId": 0,
                    "paymentIsActive": false,
                    "paymentReference": null,
                    "paymentUrl": null,
                    "searchedMonths": 0
                  },
                  "occasionBundleQuery": {
                    "startDate": "1970-01-01T00:00:00.000Z",
                    "searchedMonths": 0,
                    "locationId": 1000071,
                    "nearbyLocationIds": [],
                    "vehicleTypeId": 2,
                    "tachographTypeId": 1,
                    "occasionChoiceId": 1,
                    "examinationTypeId": 12
                  }
                }
            """;

    @Autowired
    private Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        testFinder = notLoadBalancedWebClientBuilder
                .baseUrl("https://fp.trafikverket.se")
                .codecs(codec -> codec.defaultCodecs().maxInMemorySize(2024 * 2024))
                .build();

       String telegramBotToken = Optional.ofNullable(environment.getProperty("telegram_bot_token")).orElse("WRONG");
       String telegramBaseUrl = "https://api.telegram.org/%s".formatted(telegramBotToken);
        telegramBotClient = notLoadBalancedWebClientBuilder
                .baseUrl(telegramBaseUrl)
                .codecs(codec -> codec.defaultCodecs().maxInMemorySize(2024 * 2024))
                .build();

        notifyIfFoundExam()
                .map(Occasion::summary)
                .subscribe();


        Flux.interval(Duration.ofMinutes(30))
                .flatMapSequential(ignore -> notifyIfFoundExam())
                .map(Occasion::summary)
                .subscribe();
    }

    private Flux<Occasion> notifyIfFoundExam() {
        String timeWindowStart = Optional.ofNullable(environment.getProperty("time_window_start"))
                .orElseGet(() -> "2022-04-01");
        String timeWindowEnd = Optional.ofNullable(environment.getProperty("time_window_end"))
                .orElseGet(() -> "2022-06-30");
        return loadExams()
                .filter(AvailableExamsResponse::isOk)
                .map(AvailableExamsResponse::data)
                .flatMapIterable(Data::bundles)
                .flatMapIterable(Bundle::occasions)
                .filter(Occasion::isAroundUppsala)
                .filter(exam -> exam.date().isAfter(LocalDate.now()))
                .filter(exam -> exam.date().isBefore(LocalDate.now().plusMonths(6)))
                .doOnNext(exam -> {
                    if (exam.date().isAfter(LocalDate.parse(timeWindowStart))
                            && exam.date().isBefore(LocalDate.parse(timeWindowEnd))) {
                        var message = "!!!!!!!!!!!!!!!!!!!!!!!!\n-------\n new suitable exam found on " + exam.summary();
                        notifyUsingTelegramBot(message);
                    }
                })
                .doOnNext(exam -> LOGGER.info(exam.summary()))
//                .doOnNext(exam -> notifyUsingTelegramBot(exam.summary()))
                .doOnError(e -> LOGGER.error("error while sorting exams", e));
    }

    private void notifyUsingTelegramBot(String text) {
        String chatId = Optional.ofNullable(environment.getProperty("chat_id"))
                .orElseGet(() -> "72624148");//41846159 72624148
        telegramBotClient.post()
                .uri("/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "chat_id": %s,
                          "text": "%s"
                        }
                        """.formatted(chatId, text))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> LOGGER.error("error while sending telegram message", e))
                .subscribe();
    }

    private Mono<AvailableExamsResponse> loadExams() {
        String personNumber = Optional.ofNullable(environment.getProperty("ssn")).orElse("WRONG");
        String requestBody = Find_PERSIAN_Theory_Exams_IN_UPPSALA_Request_Body.formatted(personNumber);
        return testFinder.post()
                .uri("/Boka/occasion-bundles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .header("Referer","https://fp.trafikverket.se/Boka/")
                .header("Origin","https://fp.trafikverket.se")
                .header("sec-ch-ua-platform","\"macOS\"")
                .header("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.80 Safari/537.36")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(AvailableExamsResponse.class)
//                .doOnNext(System.out::println)
                .doOnError(e -> LOGGER.error("error while loading exams", e));
    }
}


