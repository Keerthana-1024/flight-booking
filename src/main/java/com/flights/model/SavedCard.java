package com.flights.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "saved_cards")
@Data
@NoArgsConstructor
public class SavedCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "stripe_payment_method_id", nullable = false, length = 255)
    private String stripePaymentMethodId;

    @Column(name = "card_brand", length = 50)
    private String cardBrand; // visa, mastercard, rupay, etc.

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "exp_month")
    private Integer expMonth;

    @Column(name = "exp_year")
    private Integer expYear;

    @Column(name = "card_type", length = 20)
    private String cardType; // CREDIT or DEBIT

    @Column(name = "nickname", length = 100)
    private String nickname; // user-defined label

    @Column(name = "is_default")
    private boolean isDefault = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
