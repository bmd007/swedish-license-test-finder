package wonderland.driving.license.test.finder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record AvailableExamsResponse(Data data, int status, String url){
    public boolean isOk(){
        return status==200;
    }
}

record Data(List<Bundle> bundles, int searchedMonths){ }

record Bundle(List<Occasion> occasions, String cost){ }

record Occasion (
        String examinationId,
        int examinationCategory,
        Duration duration,
        int examinationTypeId,
        int locationId,
        int occasionChoiceId,
        int vehicleTypeId,
        int languageId,
        int tachographTypeId,
        String name,
        String properties,
        LocalDate date,
        String time,
        String locationName,
        String cost,
        String costText,
        boolean increasedFee,
        String isEducatorBooking,
        boolean isLateCancellation,
        boolean isOutsideValidDuration,
        boolean isUsingTaxiKnowledgeValidDuration,
        String placeAddress,
        String placeCoordinate) {

    public boolean isInStockholmCity(){
        return locationId == 1000140;
    }

    public boolean isInUppsala(){
        return locationId == 1000071;
    }

   public boolean isAroundUppsala(){
        return isInUppsala() || isInStockholmCity();
    }
    
   public String summary(){
       return this.duration().startsAt().getDayOfWeek().name()
               + "     " + this.duration().startsAt().getMonth().name() + "  " + this.duration().startsAt().getDayOfMonth()
               + "  at " + this.duration().startsAt().toLocalTime()
               + "  in " + this.locationName();
   } 
}

record Duration(String start, String end){
    public LocalDateTime startsAt(){
        var simpleDataTime = start.substring(0, start.indexOf("+"));
        return LocalDateTime.parse(simpleDataTime, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }
}