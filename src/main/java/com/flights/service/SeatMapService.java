package com.flights.service;

import com.flights.dto.SeatDto;
import com.flights.model.Booking;
import com.flights.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Generates the aircraft seat map layout using a seeded RNG so the same
 * flight always has the same seats occupied.
 * Also overlays confirmed bookings from the database so booked seats show correctly.
 */
@Service
public class SeatMapService {

    private static final Set<Integer> EXIT_ROWS = Set.of(5, 16);

    @Autowired
    private BookingRepository bookingRepo;

    public List<SeatDto> generateSeatMap(String flightId) {
        long seed = flightId.chars().reduce(0, (a, b) -> a * 31 + b) * 31337L;
        Random rng = new Random(seed);

        List<SeatDto> seats = new ArrayList<>();

        // Business: rows 1–4, cols A–D
        for (int row = 1; row <= 4; row++) {
            for (char col : new char[]{'A', 'B', 'C', 'D'}) {
                SeatDto seat = new SeatDto();
                seat.setSeatNumber(row + "" + col);
                seat.setRow(row);
                seat.setCol(String.valueOf(col));
                seat.setCabin("BUSINESS");
                seat.setExitRow(false);
                double r = rng.nextDouble();
                seat.setStatus(r < 0.28 ? "OCCUPIED" : "AVAILABLE");
                seats.add(seat);
            }
        }

        // Economy: rows 5–30, cols A–F
        for (int row = 5; row <= 30; row++) {
            boolean isExit = EXIT_ROWS.contains(row);
            for (char col : new char[]{'A', 'B', 'C', 'D', 'E', 'F'}) {
                SeatDto seat = new SeatDto();
                seat.setSeatNumber(row + "" + col);
                seat.setRow(row);
                seat.setCol(String.valueOf(col));
                seat.setCabin("ECONOMY");
                seat.setExitRow(isExit);

                // Centre seats of exit row are blocked
                if (isExit && (col == 'C' || col == 'D')) {
                    seat.setStatus("BLOCKED");
                } else {
                    double boost = (col == 'C' || col == 'D') ? 0.05 : 0;
                    double r = rng.nextDouble();
                    seat.setStatus(r < 0.60 + boost ? "OCCUPIED" : "AVAILABLE");
                }

                // Extra cost for first few economy rows or exit rows
                if (row <= 7 || (isExit && !seat.getStatus().equals("BLOCKED"))) {
                    seat.setExtraCost("₹1,200");
                }

                seats.add(seat);
            }
        }

        // ── Overlay confirmed bookings from DB ─────────────────────────────
        // Build a map: seatNumber → bookingOwnerId (only confirmed, not-fully-cancelled seats)
        Map<String, Long> bookedSeatOwners = new HashMap<>();
        List<Booking> confirmedBookings = bookingRepo.findByFlightIdAndStatus(flightId, "CONFIRMED");
        for (Booking booking : confirmedBookings) {
            if (booking.getSeats() == null) continue;
            Set<String> cancelledSet = new HashSet<>();
            if (booking.getCancelledSeats() != null && !booking.getCancelledSeats().isBlank()) {
                cancelledSet.addAll(Arrays.asList(booking.getCancelledSeats().split(",")));
            }
            for (String s : booking.getSeats().split(",")) {
                String seatNum = s.trim().toUpperCase();
                if (!cancelledSet.contains(seatNum)) {
                    bookedSeatOwners.put(seatNum, booking.getUser().getId());
                }
            }
        }

        // Apply booked seat statuses (will be overlaid again in controller for BOOKED_BY_YOU)
        for (SeatDto seat : seats) {
            if (bookedSeatOwners.containsKey(seat.getSeatNumber().toUpperCase())) {
                seat.setStatus("BOOKED");
                seat.setLockedByUserId(bookedSeatOwners.get(seat.getSeatNumber().toUpperCase()));
            }
        }

        return seats;
    }
}
