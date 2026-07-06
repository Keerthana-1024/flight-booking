package com.flights.dto;

import lombok.Data;
import java.util.List;

@Data
public class FlightDto {
    private String id;
    private String flightNumber;
    private String airline;
    private String airlineName;
    private String departure;
    private String arrival;
    private String departureCity;
    private String arrivalCity;
    private String departureTime;    // ISO string
    private String arrivalTime;      // ISO string
    private int durationMinutes;     // total flight minutes
    private int stops;               // 0 = direct
    private List<StopDto> stopDetails;
    private int layoverMinutes;      // total layover duration
    private double price;            // per adult in INR
    private String currency;
    private int seatsLeft;
    private String flightDate;       // YYYY-MM-DD
    private String rawOfferId;       // for Amadeus integration

    @Data
    public static class StopDto {
        private String airportCode;
        private String airportName;
        private int layoverMinutes;
    }
}
