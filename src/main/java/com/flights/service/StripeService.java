package com.flights.service;

import com.flights.model.*;
import com.flights.repository.*;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import com.stripe.param.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;

@Service
public class StripeService {

    @Value("${stripe.secret.key:sk_test_REPLACE_ME}")
    private String secretKey;

    @Value("${stripe.publishable.key:pk_test_REPLACE_ME}")
    private String publishableKey;

    @Value("${stripe.webhook.secret:whsec_REPLACE_ME}")
    private String webhookSecret;

    @Autowired
    private SavedCardRepository cardRepo;

    @Autowired
    private PaymentRecordRepository paymentRecordRepo;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.contains("REPLACE_ME") && secretKey.startsWith("sk_");
    }

    public String getPublishableKey() {
        return publishableKey;
    }

    // ─── Customer Management ───────────────────────────────────────────────

    public String getOrCreateStripeCustomer(User user) throws Exception {
        if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
            return user.getStripeCustomerId();
        }
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(user.getName())
                .setPhone(user.getPhone())
                .putMetadata("userId", String.valueOf(user.getId()))
                .build();
        Customer customer = Customer.create(params);
        return customer.getId();
    }

    // ─── Payment Methods ───────────────────────────────────────────────────

    public List<Map<String, Object>> listSavedCards(String stripeCustomerId) throws Exception {
        PaymentMethodListParams params = PaymentMethodListParams.builder()
                .setCustomer(stripeCustomerId)
                .setType(PaymentMethodListParams.Type.CARD)
                .build();
        PaymentMethodCollection methods = PaymentMethod.list(params);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PaymentMethod pm : methods.getData()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", pm.getId());
            m.put("brand", pm.getCard() != null ? pm.getCard().getBrand() : "");
            m.put("last4", pm.getCard() != null ? pm.getCard().getLast4() : "");
            m.put("expMonth", pm.getCard() != null ? pm.getCard().getExpMonth() : 0);
            m.put("expYear", pm.getCard() != null ? pm.getCard().getExpYear() : 0);
            m.put("type", "CARD");
            result.add(m);
        }
        return result;
    }

    /**
     * Attach a new card payment method to a customer and optionally save to DB.
     */
    public Map<String, Object> attachPaymentMethod(String paymentMethodId,
                                                     String stripeCustomerId,
                                                     boolean saveCard,
                                                     String cardType,
                                                     String nickname,
                                                     User user) throws Exception {
        PaymentMethod pm = PaymentMethod.retrieve(paymentMethodId);
        pm.attach(PaymentMethodAttachParams.builder().setCustomer(stripeCustomerId).build());

        Map<String, Object> result = new HashMap<>();
        result.put("id", pm.getId());
        result.put("brand", pm.getCard() != null ? pm.getCard().getBrand() : "");
        result.put("last4", pm.getCard() != null ? pm.getCard().getLast4() : "");

        if (saveCard && user != null) {
            SavedCard sc = new SavedCard();
            sc.setUser(user);
            sc.setStripePaymentMethodId(pm.getId());
            sc.setCardBrand(pm.getCard() != null ? pm.getCard().getBrand() : "");
            sc.setCardLast4(pm.getCard() != null ? pm.getCard().getLast4() : "");
            sc.setExpMonth(pm.getCard() != null ? pm.getCard().getExpMonth().intValue() : 0);
            sc.setExpYear(pm.getCard() != null ? pm.getCard().getExpYear().intValue() : 0);
            sc.setCardType(cardType != null ? cardType : "CREDIT");
            sc.setNickname(nickname);
            sc.setDefault(cardRepo.findByUserOrderByIsDefaultDescCreatedAtDesc(user).isEmpty());
            cardRepo.save(sc);
            result.put("saved", true);
        }
        return result;
    }

    // ─── Card Payment Intent ───────────────────────────────────────────────

    public Map<String, Object> createCardPaymentIntent(String stripeCustomerId,
                                                        String paymentMethodId,
                                                        long amountPaise,
                                                        String description,
                                                        Map<String, String> metadata) throws Exception {
        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amountPaise)
                .setCurrency("inr")
                .setCustomer(stripeCustomerId)
                .setPaymentMethod(paymentMethodId)
                .setDescription(description)
                .setConfirm(true)
                .setReturnUrl("http://localhost:8080/payment-complete")
                .addPaymentMethodType("card");

        if (metadata != null) {
            metadata.forEach(builder::putMetadata);
        }

        PaymentIntent intent = PaymentIntent.create(builder.build());

        Map<String, Object> result = new HashMap<>();
        result.put("paymentIntentId", intent.getId());
        result.put("clientSecret", intent.getClientSecret());
        result.put("status", intent.getStatus());
        result.put("nextActionType", intent.getNextAction() != null ? intent.getNextAction().getType() : null);
        return result;
    }

    // ─── UPI Payment Intent ────────────────────────────────────────────────

    public Map<String, Object> createUpiPaymentIntent(String stripeCustomerId,
                                                       long amountPaise,
                                                       String description,
                                                       Map<String, String> metadata) throws Exception {
        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amountPaise)
                .setCurrency("inr")
                .setDescription(description)
                .addPaymentMethodType("upi");

        if (stripeCustomerId != null && !stripeCustomerId.isBlank()) {
            builder.setCustomer(stripeCustomerId);
        }
        if (metadata != null) {
            metadata.forEach(builder::putMetadata);
        }

        PaymentIntent intent = PaymentIntent.create(builder.build());
        Map<String, Object> result = new HashMap<>();
        result.put("paymentIntentId", intent.getId());
        result.put("clientSecret", intent.getClientSecret());
        result.put("status", intent.getStatus());
        return result;
    }

    // ─── Poll Intent Status ─────────────────────────────────────────────────

    public Map<String, Object> retrieveIntent(String paymentIntentId) throws Exception {
        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
        Map<String, Object> result = new HashMap<>();
        result.put("paymentIntentId", intent.getId());
        result.put("status", intent.getStatus());
        result.put("amount", intent.getAmount());
        result.put("currency", intent.getCurrency());
        return result;
    }

    // ─── Webhook ─────────────────────────────────────────────────────────────

    public Event constructWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        if (webhookSecret == null || webhookSecret.contains("REPLACE_ME")) {
            return null;
        }
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }

    // ─── Setup Intent (for saving card without paying) ─────────────────────

    public Map<String, Object> createSetupIntent(String stripeCustomerId) throws Exception {
        SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .addPaymentMethodType("card")
                .build();
        SetupIntent si = SetupIntent.create(params);
        Map<String, Object> result = new HashMap<>();
        result.put("clientSecret", si.getClientSecret());
        result.put("setupIntentId", si.getId());
        return result;
    }
}
