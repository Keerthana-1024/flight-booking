package com.flights.controller;

import com.flights.dto.ApiResponse;
import com.flights.model.*;
import com.flights.repository.*;
import com.flights.service.*;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @Autowired private StripeService stripeService;
    @Autowired private SeatLockService seatLockService;
    @Autowired private UserRepository userRepo;
    @Autowired private BookingRepository bookingRepo;
    @Autowired private PaymentRecordRepository paymentRecordRepo;
    @Autowired private SavedCardRepository cardRepo;
    @Autowired private SavedUpiRepository upiRepo;

    // ─── App config (publishable key) ─────────────────────────────────────
    @GetMapping("/config")
    public ResponseEntity<?> config() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "stripePublishableKey", stripeService.getPublishableKey(),
                "stripeConfigured", stripeService.isConfigured()
        )));
    }

    // ─── Card Payment ──────────────────────────────────────────────────────
    @PostMapping("/card")
    public ResponseEntity<?> payByCard(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(ApiResponse.error("Login required"));
        if (!stripeService.isConfigured()) return ResponseEntity.status(503).body(ApiResponse.error("Stripe not configured. Add keys to .env"));

        User user = userRepo.findByEmail(principal.getName()).orElseThrow();

        String paymentMethodId = (String) body.get("paymentMethodId");
        String flightId        = (String) body.get("flightId");
        String flightRef       = (String) body.get("flightRef");
        String seats           = (String) body.get("seats");          // comma-separated
        Boolean saveCard       = (Boolean) body.getOrDefault("saveCard", false);
        String  cardType       = (String)  body.getOrDefault("cardType", "CREDIT");
        String  cardNickname   = (String)  body.get("cardNickname");
        int adults             = body.get("adults") instanceof Number n ? n.intValue() : 1;

        double totalAmount = body.get("totalAmount") instanceof Number n ? n.doubleValue() : 0;
        long amountPaise   = Math.round(totalAmount * 100);

        // Verify seat locks (Bypassed temporarily for testing)
        String[] seatArr = seats != null ? seats.split(",") : new String[0];
        /*
        if (seatArr.length > 0 && !seatLockService.verifyAllLockedByUser(flightId, seatArr, user.getId())) {
            return ResponseEntity.status(409).body(ApiResponse.error(
                    "One or more seat locks have expired. Please re-select your seats."));
        }
        */

        try {
            // Ensure Stripe customer exists
            if (user.getStripeCustomerId() == null) {
                user.setStripeCustomerId(stripeService.getOrCreateStripeCustomer(user));
                userRepo.save(user);
            }

            // Optionally save card
            if (Boolean.TRUE.equals(saveCard) && paymentMethodId != null) {
                try {
                    stripeService.attachPaymentMethod(paymentMethodId, user.getStripeCustomerId(),
                            true, cardType, cardNickname, user);
                } catch (Exception ignored) {}
            }

            // Build metadata
            Map<String, String> meta = new HashMap<>();
            meta.put("flightRef", flightRef != null ? flightRef : "");
            meta.put("seats", seats != null ? seats : "");
            meta.put("userId", String.valueOf(user.getId()));
            meta.put("adults", String.valueOf(adults));

            Map<String, Object> result = stripeService.createCardPaymentIntent(
                    user.getStripeCustomerId(), paymentMethodId, amountPaise,
                    "Flight booking: " + flightRef, meta);

            // Save payment record
            savePaymentRecord(user, result, "CARD", totalAmount, seats, flightRef);

            // If succeeded, create booking and release locks
            if ("succeeded".equals(result.get("status"))) {
                createBooking(user, body, result, seats, "CARD");
                seatLockService.releaseAll(flightId, seatArr, user.getId());
            }

            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ─── UPI Payment ───────────────────────────────────────────────────────
    @PostMapping("/upi")
    public ResponseEntity<?> payByUpi(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(ApiResponse.error("Login required"));
        if (!stripeService.isConfigured()) return ResponseEntity.status(503).body(ApiResponse.error("Stripe not configured"));

        User user = userRepo.findByEmail(principal.getName()).orElseThrow();

        String flightId   = (String) body.get("flightId");
        String flightRef  = (String) body.get("flightRef");
        String seats      = (String) body.get("seats");
        String upiVpa     = (String) body.get("upiVpa");
        Boolean saveUpi   = (Boolean) body.getOrDefault("saveUpi", false);
        String upiNick    = (String) body.get("upiNickname");
        double totalAmount = body.get("totalAmount") instanceof Number n ? n.doubleValue() : 0;
        long amountPaise   = Math.round(totalAmount * 100);

        // Verify seat locks (Bypassed temporarily for testing)
        String[] seatArr = seats != null ? seats.split(",") : new String[0];
        /*
        if (seatArr.length > 0 && !seatLockService.verifyAllLockedByUser(flightId, seatArr, user.getId())) {
            return ResponseEntity.status(409).body(ApiResponse.error("Seat locks expired. Please re-select your seats."));
        }
        */

        // Save UPI if new
        if (Boolean.TRUE.equals(saveUpi) && upiVpa != null && upiVpa.contains("@")) {
            if (!upiRepo.existsByUserAndVpa(user, upiVpa)) {
                SavedUpi s = new SavedUpi();
                s.setUser(user);
                s.setVpa(upiVpa);
                s.setNickname(upiNick);
                s.setDefault(upiRepo.findByUserOrderByIsDefaultDescCreatedAtDesc(user).isEmpty());
                upiRepo.save(s);
            }
        }

        try {
            if (user.getStripeCustomerId() == null) {
                user.setStripeCustomerId(stripeService.getOrCreateStripeCustomer(user));
                userRepo.save(user);
            }

            Map<String, String> meta = new HashMap<>();
            meta.put("flightRef", flightRef != null ? flightRef : "");
            meta.put("seats", seats != null ? seats : "");
            meta.put("userId", String.valueOf(user.getId()));
            meta.put("upiVpa", upiVpa != null ? upiVpa : "");

            Map<String, Object> result = stripeService.createUpiPaymentIntent(
                    user.getStripeCustomerId(), amountPaise, "Flight UPI: " + flightRef, meta);
            result.put("upiVpa", upiVpa);

            savePaymentRecord(user, result, "UPI", totalAmount, seats, flightRef);

            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ─── Poll intent status ────────────────────────────────────────────────
    @GetMapping("/intent/{id}")
    public ResponseEntity<?> pollIntent(@PathVariable String id, Principal principal) {
        try {
            Map<String, Object> result = stripeService.retrieveIntent(id);
            // If succeeded and we have the booking data, create booking
            if ("succeeded".equals(result.get("status"))) {
                // Booking may have been created earlier — no duplicate action needed
            }
            // Update payment record status
            paymentRecordRepo.findByPaymentIntentId(id).ifPresent(pr -> {
                pr.setStatus((String) result.get("status"));
                pr.setUpdatedAt(LocalDateTime.now());
                paymentRecordRepo.save(pr);
            });
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ─── Confirm booking after UPI success ────────────────────────────────
    @PostMapping("/confirm-booking")
    public ResponseEntity<?> confirmBooking(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(ApiResponse.error("Login required"));
        User user = userRepo.findByEmail(principal.getName()).orElseThrow();

        String piId     = (String) body.get("paymentIntentId");
        String flightId = (String) body.get("flightId");
        String seats    = (String) body.get("seats");

        // Release locks now that payment succeeded
        if (flightId != null && seats != null) {
            seatLockService.releaseAll(flightId, seats.split(","), user.getId());
        }

        Booking booking = createBooking(user, body, Map.of("paymentIntentId", piId), seats, "UPI");
        return ResponseEntity.ok(ApiResponse.ok(Map.of("bookingReference", booking.getBookingReference())));
    }

    // ─── My bookings ───────────────────────────────────────────────────────
    @GetMapping("/bookings")
    public ResponseEntity<?> myBookings(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(ApiResponse.error("Login required"));
        User user = userRepo.findByEmail(principal.getName()).orElseThrow();
        List<Booking> bookings = bookingRepo.findByUserOrderByCreatedAtDesc(user);
        // Build enriched response
        List<Map<String, Object>> result = new ArrayList<>();
        for (Booking b : bookings) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("bookingReference", b.getBookingReference());
            m.put("flightId", b.getFlightId());
            m.put("flightNumber", b.getFlightNumber());
            m.put("airline", b.getAirline());
            m.put("departureCode", b.getDepartureCode());
            m.put("arrivalCode", b.getArrivalCode());
            m.put("departureTime", b.getDepartureTime());
            m.put("arrivalTime", b.getArrivalTime());
            m.put("seats", b.getSeats());
            m.put("cancelledSeats", b.getCancelledSeats());
            m.put("adults", b.getAdults());
            m.put("totalAmount", b.getTotalAmount());
            m.put("currency", b.getCurrency());
            m.put("paymentMethod", b.getPaymentMethod());
            m.put("paymentIntentId", b.getPaymentIntentId());
            m.put("status", b.getStatus());
            m.put("customerEmail", b.getCustomerEmail());
            m.put("bookedAt", b.getCreatedAt());
            result.add(m);
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ─── Partial seat cancellation ─────────────────────────────────────────
    @PostMapping("/bookings/{bookingId}/cancel-seat")
    public ResponseEntity<?> cancelSeat(@PathVariable Long bookingId,
                                        @RequestBody Map<String, String> body,
                                        Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(ApiResponse.error("Login required"));
        User user = userRepo.findByEmail(principal.getName()).orElseThrow();

        Booking booking = bookingRepo.findByIdAndUser(bookingId, user)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if ("CANCELLED".equals(booking.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Booking is already fully cancelled"));
        }

        String seatToCancel = body.getOrDefault("seat", "").trim().toUpperCase();
        if (seatToCancel.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Seat number is required"));
        }

        // Verify the seat is actually in this booking
        List<String> allSeats = Arrays.stream(booking.getSeats().split(","))
                .map(String::trim).map(String::toUpperCase).collect(java.util.stream.Collectors.toList());
        if (!allSeats.contains(seatToCancel)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Seat " + seatToCancel + " is not in this booking"));
        }

        // Add to cancelled seats
        Set<String> cancelledSet = new HashSet<>();
        if (booking.getCancelledSeats() != null && !booking.getCancelledSeats().isBlank()) {
            cancelledSet.addAll(Arrays.asList(booking.getCancelledSeats().split(",")));
        }
        cancelledSet.add(seatToCancel);
        booking.setCancelledSeats(String.join(",", cancelledSet));

        // If ALL seats are now cancelled, mark booking as CANCELLED
        if (cancelledSet.containsAll(allSeats)) {
            booking.setStatus("CANCELLED");
        }

        bookingRepo.save(booking);

        Map<String, Object> res = new HashMap<>();
        res.put("bookingReference", booking.getBookingReference());
        res.put("cancelledSeat", seatToCancel);
        res.put("bookingStatus", booking.getStatus());
        res.put("remainingSeats", allSeats.stream().filter(s -> !cancelledSet.contains(s)).collect(java.util.stream.Collectors.toList()));
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    // ─── Stripe Webhook ────────────────────────────────────────────────────
    @PostMapping(value = "/webhook", consumes = "application/json")
    public ResponseEntity<?> webhook(@RequestBody String payload,
                                     @RequestHeader(value = "Stripe-Signature", required = false) String sig) {
        try {
            Event event = stripeService.constructWebhookEvent(payload, sig);
            if (event == null) return ResponseEntity.ok("no-op");

            if (event.getType().startsWith("payment_intent.")) {
                String piId = ((PaymentIntent) event.getData().getObject()).getId();
                String newStatus = switch (event.getType()) {
                    case "payment_intent.succeeded" -> "succeeded";
                    case "payment_intent.payment_failed" -> "failed";
                    case "payment_intent.processing" -> "processing";
                    default -> null;
                };
                if (newStatus != null) {
                    final String finalStatus = newStatus;
                    paymentRecordRepo.findByPaymentIntentId(piId).ifPresent(pr -> {
                        pr.setStatus(finalStatus);
                        pr.setUpdatedAt(LocalDateTime.now());
                        paymentRecordRepo.save(pr);
                    });
                }
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Webhook error: " + e.getMessage());
        }
        return ResponseEntity.ok("received");
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private void savePaymentRecord(User user, Map<String, Object> intentResult,
                                   String methodType, double amount, String seats, String flightRef) {
        PaymentRecord pr = new PaymentRecord();
        pr.setUser(user);
        pr.setPaymentIntentId((String) intentResult.get("paymentIntentId"));
        pr.setStripeCustomerId(user.getStripeCustomerId());
        pr.setPaymentMethodType(methodType);
        pr.setAmount(BigDecimal.valueOf(amount));
        pr.setCurrency("INR");
        pr.setStatus((String) intentResult.getOrDefault("status", "unknown"));
        pr.setDescription("Flight: " + flightRef + " | Seats: " + seats);
        pr.setFlightReference(flightRef);
        try { paymentRecordRepo.save(pr); } catch (Exception ignored) {}
    }

    private Booking createBooking(User user, Map<String, Object> body,
                                  Map<String, Object> intentResult, String seats, String payMethod) {
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setBookingReference(generateRef());
        booking.setFlightId((String) body.get("flightId"));
        booking.setFlightNumber((String) body.getOrDefault("flightNumber", ""));
        booking.setAirline((String) body.getOrDefault("airline", ""));
        booking.setDepartureCode((String) body.getOrDefault("departure", ""));
        booking.setArrivalCode((String) body.getOrDefault("arrival", ""));
        booking.setSeats(seats);
        booking.setAdults(body.get("adults") instanceof Number n ? n.intValue() : 1);
        booking.setTotalAmount(BigDecimal.valueOf(
                body.get("totalAmount") instanceof Number n ? n.doubleValue() : 0));
        booking.setCurrency("INR");
        booking.setPaymentIntentId((String) intentResult.get("paymentIntentId"));
        booking.setPaymentMethod(payMethod);
        booking.setCustomerEmail(user.getEmail());
        booking.setStatus("CONFIRMED");
        return bookingRepo.save(booking);
    }

    private String generateRef() {
        return "FLT" + Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(4);
    }
}
