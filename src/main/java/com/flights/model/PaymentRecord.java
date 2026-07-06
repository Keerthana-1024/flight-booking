package com.flights.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_records")
@Data
@NoArgsConstructor
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "payment_intent_id", unique = true, length = 255)
    private String paymentIntentId;

    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    @Column(name = "payment_method_type", length = 20)
    private String paymentMethodType; // CARD or UPI

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 5)
    private String currency;

    @Column(name = "status", length = 50)
    private String status; // succeeded, failed, processing, requires_action

    @Column(name = "card_brand", length = 50)
    private String cardBrand;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "upi_vpa", length = 255)
    private String upiVpa;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "flight_reference", length = 100)
    private String flightReference;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
