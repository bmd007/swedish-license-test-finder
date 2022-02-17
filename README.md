# swedish-license-test-finder
swedish-driving-license-test-finder


build: ./gradlew bootJar

run: java -jar -Dssn=[person number] -Dtelegram_bot_token=[token for your telegram bot]  ./build/libs/license-test-finder-beta.jar
