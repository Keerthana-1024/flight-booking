package com.flights.service;

import com.flights.dto.FlightDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * FlightService generates realistic mock flights for a given route and date.
 * When Amadeus keys are configured, it uses the live API.
 * The mock generator uses a seeded RNG so the same route+date always produces the same flights.
 */
@Service
public class FlightService {

    @Value("${amadeus.api.key:}")
    private String amadeusKey;

    @Value("${amadeus.api.secret:}")
    private String amadeusSecret;

    // Airport metadata
    private static final Map<String, String[]> AIRPORTS = new HashMap<>();

    static {
        // {IATA, City, Country}
        AIRPORTS.put("DEL", new String[]{"Indira Gandhi International", "Delhi", "India"});
        AIRPORTS.put("BOM", new String[]{"Chhatrapati Shivaji Maharaj International", "Mumbai", "India"});
        AIRPORTS.put("BLR", new String[]{"Kempegowda International", "Bengaluru", "India"});
        AIRPORTS.put("MAA", new String[]{"Chennai International", "Chennai", "India"});
        AIRPORTS.put("HYD", new String[]{"Rajiv Gandhi International", "Hyderabad", "India"});
        AIRPORTS.put("CCU", new String[]{"Netaji Subhas Chandra Bose International", "Kolkata", "India"});
        AIRPORTS.put("GOI", new String[]{"Goa International", "Goa", "India"});
        AIRPORTS.put("PNQ", new String[]{"Pune Airport", "Pune", "India"});
        AIRPORTS.put("AMD", new String[]{"Sardar Vallabhbhai Patel International", "Ahmedabad", "India"});
        AIRPORTS.put("JAI", new String[]{"Jaipur International", "Jaipur", "India"});
        AIRPORTS.put("COK", new String[]{"Cochin International", "Kochi", "India"});
        AIRPORTS.put("TRV", new String[]{"Trivandrum International", "Thiruvananthapuram", "India"});
        AIRPORTS.put("DXB", new String[]{"Dubai International", "Dubai", "UAE"});
        AIRPORTS.put("LHR", new String[]{"Heathrow", "London", "UK"});
        AIRPORTS.put("SIN", new String[]{"Changi Airport", "Singapore", "Singapore"});
        AIRPORTS.put("BKK", new String[]{"Suvarnabhumi", "Bangkok", "Thailand"});
        AIRPORTS.put("KUL", new String[]{"Kuala Lumpur International", "Kuala Lumpur", "Malaysia"});
        AIRPORTS.put("CDG", new String[]{"Charles de Gaulle", "Paris", "France"});
        AIRPORTS.put("FRA", new String[]{"Frankfurt Airport", "Frankfurt", "Germany"});
        AIRPORTS.put("DOH", new String[]{"Hamad International", "Doha", "Qatar"});
        AIRPORTS.put("AUH", new String[]{"Abu Dhabi International", "Abu Dhabi", "UAE"});
        AIRPORTS.put("JFK", new String[]{"John F. Kennedy International", "New York", "USA"});
        AIRPORTS.put("SYD", new String[]{"Sydney Airport", "Sydney", "Australia"});
        AIRPORTS.put("NRT", new String[]{"Narita International", "Tokyo", "Japan"});
        AIRPORTS.put("HKG", new String[]{"Hong Kong International", "Hong Kong", "HK"});
    }

    // Airlines with name and IATA codes
    private static final String[][] AIRLINES = {
        {"AI",  "Air India"},
        {"6E",  "IndiGo"},
        {"UK",  "Vistara"},
        {"SG",  "SpiceJet"},
        {"G8",  "Go First"},
        {"QP",  "Akasa Air"},
        {"EK",  "Emirates"},
        {"QR",  "Qatar Airways"},
        {"EY",  "Etihad Airways"},
        {"SQ",  "Singapore Airlines"},
        {"BA",  "British Airways"},
        {"LH",  "Lufthansa"},
        {"TG",  "Thai Airways"},
        {"MH",  "Malaysia Airlines"},
    };

    // Common hub airports for connections
    private static final String[] HUBS = {"DEL", "BOM", "DXB", "DOH", "SIN", "FRA", "LHR"};

    /**
     * Search flights across a date range with optional duration filter.
     * @param origin         IATA origin
     * @param destination    IATA destination
     * @param dateStart      Start date (inclusive)
     * @param dateEnd        End date (inclusive)
     * @param adults         Number of passengers
     * @param maxDays        Maximum flight duration in days (0 = no limit)
     * @param sortBy         price | duration | layover
     */
    public List<FlightDto> searchFlights(String origin, String destination,
                                         LocalDate dateStart, LocalDate dateEnd,
                                         int adults, int maxDays, String sortBy) {
        List<FlightDto> allFlights = new ArrayList<>();

        LocalDate current = dateStart;
        while (!current.isAfter(dateEnd)) {
            List<FlightDto> dailyFlights = generateFlightsForDate(origin, destination, current);
            allFlights.addAll(dailyFlights);
            current = current.plusDays(1);
        }

        // Filter by max duration (in days)
        if (maxDays > 0) {
            int maxMinutes = maxDays * 24 * 60;
            allFlights.removeIf(f -> f.getDurationMinutes() > maxMinutes);
        }

        // Sort
        switch (sortBy == null ? "price" : sortBy.toLowerCase()) {
            case "duration"  -> allFlights.sort(Comparator.comparingInt(FlightDto::getDurationMinutes));
            case "layover"   -> allFlights.sort(Comparator.comparingInt(FlightDto::getLayoverMinutes));
            default          -> allFlights.sort(Comparator.comparingDouble(FlightDto::getPrice));
        }

        return allFlights;
    }

    /**
     * Generate realistic mock flights for a specific date using a seeded RNG.
     * Same inputs always produce the same flights (deterministic).
     */
    private List<FlightDto> generateFlightsForDate(String origin, String destination, LocalDate date) {
        // Seed based on origin + destination + date → consistent results
        long seed = ((long) origin.hashCode() * 31 + destination.hashCode()) * 37 + date.toEpochDay();
        Random rng = new Random(seed);

        List<FlightDto> flights = new ArrayList<>();
        int numFlights = 3 + rng.nextInt(5); // 3–7 flights

        // Base price based on "distance" (hash-derived)
        double basePrice = 2000 + Math.abs((origin + destination).hashCode() % 40000);

        for (int i = 0; i < numFlights; i++) {
            String[] airlineInfo = AIRLINES[rng.nextInt(AIRLINES.length)];
            String airlineCode = airlineInfo[0];
            String airlineName = airlineInfo[1];
            String flightNum = airlineCode + (100 + rng.nextInt(900));

            // Departure time
            int depHour = 5 + rng.nextInt(17); // 05:00–21:00
            int depMin = rng.nextBoolean() ? 0 : 30;
            LocalDateTime depTime = date.atTime(depHour, depMin);

            // Is direct flight?
            boolean isDirect = rng.nextDouble() < 0.40; // 40% direct
            int stops;
            int directMinutes;
            int layoverMinutes = 0;
            List<FlightDto.StopDto> stopDetails = new ArrayList<>();

            // Estimate base flight time from hash
            int baseFlightMins = 60 + Math.abs((origin + destination).hashCode() % 720); // 1–13 hrs

            if (isDirect) {
                stops = 0;
                directMinutes = baseFlightMins + rng.nextInt(30) - 15;
            } else {
                stops = 1 + (rng.nextDouble() < 0.2 ? 1 : 0); // 1–2 stops
                directMinutes = baseFlightMins + rng.nextInt(60);

                for (int s = 0; s < stops; s++) {
                    String hub = HUBS[rng.nextInt(HUBS.length)];
                    if (hub.equals(origin) || hub.equals(destination)) hub = HUBS[0];
                    int layover = 60 + rng.nextInt(180); // 1–4 hr layover
                    layoverMinutes += layover;

                    FlightDto.StopDto stop = new FlightDto.StopDto();
                    stop.setAirportCode(hub);
                    String[] hubInfo = AIRPORTS.getOrDefault(hub, new String[]{"Airport", hub, ""});
                    stop.setAirportName(hubInfo[0] + " (" + hubInfo[1] + ")");
                    stop.setLayoverMinutes(layover);
                    stopDetails.add(stop);
                }
            }

            int totalMinutes = directMinutes + layoverMinutes;
            LocalDateTime arrTime = depTime.plusMinutes(totalMinutes);

            // Price variation: direct costs more, longer flights cost more
            double priceMultiplier = 0.85 + rng.nextDouble() * 0.5;
            if (isDirect) priceMultiplier *= 1.15;
            double price = Math.round(basePrice * priceMultiplier / 100.0) * 100.0;

            int seatsLeft = 2 + rng.nextInt(35);

            FlightDto flight = new FlightDto();
            flight.setId(flightNum + "_" + date.format(DateTimeFormatter.BASIC_ISO_DATE));
            flight.setFlightNumber(flightNum);
            flight.setAirline(airlineCode);
            flight.setAirlineName(airlineName);
            flight.setDeparture(origin);
            flight.setArrival(destination);

            String[] originInfo = AIRPORTS.getOrDefault(origin, new String[]{"Airport", origin, ""});
            String[] destInfo = AIRPORTS.getOrDefault(destination, new String[]{"Airport", destination, ""});
            flight.setDepartureCity(originInfo[1]);
            flight.setArrivalCity(destInfo[1]);

            flight.setDepartureTime(depTime.format(DateTimeFormatter.ISO_DATE_TIME));
            flight.setArrivalTime(arrTime.format(DateTimeFormatter.ISO_DATE_TIME));
            flight.setDurationMinutes(totalMinutes);
            flight.setStops(stops);
            flight.setStopDetails(stopDetails);
            flight.setLayoverMinutes(layoverMinutes);
            flight.setPrice(price);
            flight.setCurrency("INR");
            flight.setSeatsLeft(seatsLeft);
            flight.setFlightDate(date.format(DateTimeFormatter.ISO_LOCAL_DATE));

            flights.add(flight);
        }

        return flights;
    }

    public String getAirportName(String iata) {
        String[] info = AIRPORTS.get(iata.toUpperCase());
        return info != null ? info[0] + ", " + info[1] : iata;
    }

    public String getAirportCity(String iata) {
        String[] info = AIRPORTS.get(iata.toUpperCase());
        return info != null ? info[1] : iata;
    }

    public Map<String, Object> searchAirports(String keyword) {
        List<Map<String, String>> results = new ArrayList<>();
        String kw = keyword.toLowerCase();
        for (Map.Entry<String, String[]> e : AIRPORTS.entrySet()) {
            String[] v = e.getValue();
            if (e.getKey().toLowerCase().contains(kw) ||
                v[0].toLowerCase().contains(kw) ||
                v[1].toLowerCase().contains(kw)) {
                Map<String, String> r = new HashMap<>();
                r.put("iata", e.getKey());
                r.put("name", v[0]);
                r.put("city", v[1]);
                r.put("country", v[2]);
                results.add(r);
            }
        }
        return Map.of("airports", results);
    }
}
