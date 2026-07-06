package com.flights.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "booking_reference", unique = true, length = 20)
    private String bookingReference;

    @Column(name = "flight_number", length = 20)
    private String flightNumber;

    @Column(name = "flight_id", length = 50)
    private String flightId; // e.g. AI123_20260706 — uniquely identifies a flight+date

    @Column(name = "airline", length = 10)
    private String airline;

    @Column(name = "departure_code", length = 5)
    private String departureCode;

    @Column(name = "arrival_code", length = 5)
    private String arrivalCode;

    @Column(name = "cancelled_seats", length = 500)
    private String cancelledSeats; // comma-separated seats that have been partially cancelled

    @Column(name = "departure_time")
    private LocalDateTime departureTime;

    @Column(name = "arrival_time")
    private LocalDateTime arrivalTime;

    @Column(name = "seats", length = 500)
    private String seats; // comma-separated seat numbers

    @Column(name = "adults")
    private Integer adults;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", length = 5)
    private String currency = "INR";

    @Column(name = "payment_intent_id", length = 255)
    private String paymentIntentId;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod; // CARD or UPI

    @Column(name = "status", length = 30)
    private String status = "CONFIRMED"; // CONFIRMED, CANCELLED

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
