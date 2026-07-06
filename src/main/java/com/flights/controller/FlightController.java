package com.flights.controller;

import com.flights.dto.*;
import com.flights.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/flights")
public class FlightController {

    @Autowired private FlightService flightService;
    @Autowired private SeatMapService seatMapService;
    @Autowired private SeatLockService seatLockService;

    // ─── Airport search autocomplete ────────────────────────────────────────
    @GetMapping("/airports")
    public ResponseEntity<?> searchAirports(@RequestParam String keyword) {
        return ResponseEntity.ok(ApiResponse.ok(flightService.searchAirports(keyword)));
    }

    // ─── Flight search ─────────────────────────────────────────────────────
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd,
            @RequestParam(defaultValue = "1") int adults,
            @RequestParam(defaultValue = "0") int maxDays,
            @RequestParam(defaultValue = "price") String sortBy
    ) {
        if (dateEnd == null) dateEnd = dateStart;
        if (dateEnd.isBefore(dateStart)) dateEnd = dateStart;
        if (dateEnd.isAfter(dateStart.plusDays(30))) dateEnd = dateStart.plusDays(30); // limit to 30 days

        List<FlightDto> flights = flightService.searchFlights(
                origin.toUpperCase(), destination.toUpperCase(),
                dateStart, dateEnd, adults, maxDays, sortBy
        );

        Map<String, Object> result = new HashMap<>();
        result.put("count", flights.size());
        result.put("flights", flights);
        result.put("origin", origin.toUpperCase());
        result.put("destination", destination.toUpperCase());
        result.put("originCity", flightService.getAirportCity(origin));
        result.put("destinationCity", flightService.getAirportCity(destination));
        result.put("dateStart", dateStart.toString());
        result.put("dateEnd", dateEnd.toString());
        result.put("adults", adults);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ─── Seat map for a flight ─────────────────────────────────────────────
    @GetMapping("/seatmap/{flightId}")
    public ResponseEntity<?> getSeatMap(@PathVariable String flightId, Principal principal) {
        List<SeatDto> seats = seatMapService.generateSeatMap(flightId);

        Long currentUserId = principal != null ? getCurrentUserId(principal.getName()) : null;

        // Build a map of bookingRef for the current user's booked seats on this flight
        Map<String, String> userSeatBookingRef = new HashMap<>();
        if (currentUserId != null) {
            bookingRepo.findByFlightIdAndStatus(flightId, "CONFIRMED").stream()
                .filter(b -> b.getUser().getId().equals(currentUserId))
                .forEach(b -> {
                    if (b.getSeats() != null) {
                        java.util.Set<String> cancelled = new java.util.HashSet<>();
                        if (b.getCancelledSeats() != null) java.util.Arrays.stream(b.getCancelledSeats().split(",")).forEach(s -> cancelled.add(s.trim().toUpperCase()));
                        java.util.Arrays.stream(b.getSeats().split(","))
                            .map(String::trim).map(String::toUpperCase)
                            .filter(s -> !cancelled.contains(s))
                            .forEach(s -> userSeatBookingRef.put(s, b.getBookingReference()));
                    }
                });
        }

        for (SeatDto seat : seats) {
            String seatUpper = seat.getSeatNumber().toUpperCase();

            // DB-booked seat owned by current user → BOOKED_BY_YOU
            if (seat.getStatus().equals("BOOKED") && currentUserId != null
                    && currentUserId.equals(seat.getLockedByUserId())) {
                seat.setStatus("BOOKED_BY_YOU");
                seat.setBookingReference(userSeatBookingRef.get(seatUpper));
                continue;
            }

            // Overlay Redis lock status for AVAILABLE seats
            if (!seat.getStatus().equals("OCCUPIED") && !seat.getStatus().equals("BLOCKED")
                    && !seat.getStatus().equals("BOOKED")) {
                String owner = seatLockService.getLockOwner(flightId, seat.getSeatNumber());
                if (owner != null) {
                    long ttl = seatLockService.getLockTtl(flightId, seat.getSeatNumber());
                    if (currentUserId != null && owner.equals(String.valueOf(currentUserId))) {
                        seat.setStatus("SELECTED");
                    } else {
                        seat.setStatus("LOCKED");
                    }
                    seat.setLockSecondsLeft((int) ttl);
                    seat.setLockedByUserId(Long.parseLong(owner));
                }
            }
        }

        // Group into rows
        Map<Integer, List<SeatDto>> byRow = new LinkedHashMap<>();
        for (SeatDto seat : seats) {
            byRow.computeIfAbsent(seat.getRow(), k -> new ArrayList<>()).add(seat);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("flightId", flightId);
        result.put("totalSeats", seats.size());
        result.put("rows", byRow);
        result.put("available", seats.stream().filter(s -> s.getStatus().equals("AVAILABLE")).count());
        result.put("occupied", seats.stream().filter(s -> s.getStatus().equals("OCCUPIED")).count());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ─── Lock a seat ───────────────────────────────────────────────────────
    @PostMapping("/seats/lock")
    public ResponseEntity<?> lockSeat(@RequestBody Map<String, String> body, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(ApiResponse.error("Login required"));
        Long userId = getCurrentUserId(principal.getName());
        String flightId = body.get("flightId");
        String seatNumber = body.get("seatNumber");

        boolean locked = seatLockService.lockSeat(flightId, seatNumber, userId);
        if (locked) {
            long ttl = seatLockService.getLockTtl(flightId, seatNumber);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("locked", true, "ttlSeconds", ttl, "seatNumber", seatNumber)));
        }
        return ResponseEntity.status(409).body(ApiResponse.error("Seat " + seatNumber + " is locked by another passenger"));
    }

    // ─── Unlock a seat ─────────────────────────────────────────────────────
    @PostMapping("/seats/unlock")
    public ResponseEntity<?> unlockSeat(@RequestBody Map<String, String> body, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(ApiResponse.error("Login required"));
        Long userId = getCurrentUserId(principal.getName());
        String flightId = body.get("flightId");
        String seatNumber = body.get("seatNumber");
        seatLockService.unlockSeat(flightId, seatNumber, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("unlocked", true)));
    }

    // ─── Config (publishable key etc.) ────────────────────────────────────
    @GetMapping("/config")
    public ResponseEntity<?> getConfig(
            @Autowired com.flights.service.StripeService stripeService
    ) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "stripePublishableKey", stripeService.getPublishableKey(),
                "stripeConfigured", stripeService.isConfigured()
        )));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.flights.repository.UserRepository userRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private com.flights.repository.BookingRepository bookingRepo;

    private Long getCurrentUserId(String email) {
        return userRepo.findByEmail(email).map(u -> u.getId()).orElse(-1L);
    }
}
