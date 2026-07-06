package com.flights.controller;

import com.flights.dto.*;
import com.flights.model.*;
import com.flights.repository.*;
import com.flights.service.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private UserRepository userRepo;
    @Autowired private SavedCardRepository cardRepo;
    @Autowired private SavedUpiRepository upiRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private StripeService stripeService;

    // ─── Register ──────────────────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email already registered"));
        }
        User user = new User();
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setName(req.getName());
        user.setPhone(req.getPhone());

        // Create Stripe customer if configured
        if (stripeService.isConfigured()) {
            try {
                String customerId = stripeService.getOrCreateStripeCustomer(user);
                user.setStripeCustomerId(customerId);
            } catch (Exception e) {
                // non-fatal
            }
        }

        user = userRepo.save(user);
        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(buildAuthResponse(user, token)));
    }

    // ─── Login ─────────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        Optional<User> optUser = userRepo.findByEmail(req.getEmail());
        if (optUser.isEmpty() || !passwordEncoder.matches(req.getPassword(), optUser.get().getPassword())) {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid email or password"));
        }
        User user = optUser.get();
        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(buildAuthResponse(user, token)));
    }

    // ─── Profile ───────────────────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<?> getProfile(Principal principal) {
        User user = userRepo.findByEmail(principal.getName()).orElseThrow();
        return ResponseEntity.ok(ApiResponse.ok(buildProfileResponse(user)));
    }

    // ─── Add UPI ───────────────────────────────────────────────────────────
    @PostMapping("/upi")
    public ResponseEntity<?> addUpi(@RequestBody Map<String, String> body, Principal principal) {
        User user = userRepo.findByEmail(principal.getName()).orElseThrow();
        String vpa = body.get("vpa");
        String nickname = body.get("nickname");

        if (vpa == null || !vpa.contains("@")) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid UPI ID format"));
        }
        if (upiRepo.existsByUserAndVpa(user, vpa)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("UPI ID already saved"));
        }

        SavedUpi upi = new SavedUpi();
        upi.setUser(user);
        upi.setVpa(vpa);
        upi.setNickname(nickname);
        boolean isFirst = upiRepo.findByUserOrderByIsDefaultDescCreatedAtDesc(user).isEmpty();
        upi.setDefault(isFirst);
        upiRepo.save(upi);

        return ResponseEntity.ok(ApiResponse.ok("UPI ID saved", buildProfileResponse(user)));
    }

    // ─── Delete UPI ────────────────────────────────────────────────────────
    @DeleteMapping("/upi/{id}")
    public ResponseEntity<?> deleteUpi(@PathVariable Long id, Principal principal) {
        User user = userRepo.findByEmail(principal.getName()).orElseThrow();
        upiRepo.deleteByIdAndUser(id, user);
        return ResponseEntity.ok(ApiResponse.ok("UPI removed", buildProfileResponse(user)));
    }

    // ─── Delete Card ───────────────────────────────────────────────────────
    @DeleteMapping("/card/{id}")
    public ResponseEntity<?> deleteCard(@PathVariable Long id, Principal principal) {
        User user = userRepo.findByEmail(principal.getName()).orElseThrow();
        cardRepo.deleteByIdAndUser(id, user);
        return ResponseEntity.ok(ApiResponse.ok("Card removed", buildProfileResponse(user)));
    }

    // ─── Setup Intent (to save card without paying) ────────────────────────
    @PostMapping("/setup-intent")
    public ResponseEntity<?> setupIntent(Principal principal) {
        User user = userRepo.findByEmail(principal.getName()).orElseThrow();
        if (!stripeService.isConfigured()) {
            return ResponseEntity.ok(ApiResponse.error("Stripe not configured"));
        }
        try {
            if (user.getStripeCustomerId() == null) {
                String cid = stripeService.getOrCreateStripeCustomer(user);
                user.setStripeCustomerId(cid);
                userRepo.save(user);
            }
            Map<String, Object> si = stripeService.createSetupIntent(user.getStripeCustomerId());
            return ResponseEntity.ok(ApiResponse.ok(si));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ─── Save Card after Setup ─────────────────────────────────────────────
    @PostMapping("/save-card")
    public ResponseEntity<?> saveCard(@RequestBody Map<String, Object> body, Principal principal) {
        User user = userRepo.findByEmail(principal.getName()).orElseThrow();
        String pmId = (String) body.get("paymentMethodId");
        String cardType = (String) body.getOrDefault("cardType", "CREDIT");
        String nickname = (String) body.get("nickname");
        if (!stripeService.isConfigured() || pmId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Missing paymentMethodId or Stripe not configured"));
        }
        try {
            if (user.getStripeCustomerId() == null) {
                String cid = stripeService.getOrCreateStripeCustomer(user);
                user.setStripeCustomerId(cid);
                userRepo.save(user);
            }
            stripeService.attachPaymentMethod(pmId, user.getStripeCustomerId(), true, cardType, nickname, user);
            return ResponseEntity.ok(ApiResponse.ok("Card saved", buildProfileResponse(user)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private Map<String, Object> buildAuthResponse(User user, String token) {
        Map<String, Object> m = new HashMap<>();
        m.put("token", token);
        m.put("user", buildProfileResponse(user));
        return m;
    }

    private Map<String, Object> buildProfileResponse(User user) {
        // Reload fresh from DB to get collections
        User fresh = userRepo.findByEmail(user.getEmail()).orElse(user);

        List<Map<String, Object>> cards = new ArrayList<>();
        for (SavedCard c : cardRepo.findByUserOrderByIsDefaultDescCreatedAtDesc(fresh)) {
            Map<String, Object> cm = new HashMap<>();
            cm.put("id", c.getId());
            cm.put("paymentMethodId", c.getStripePaymentMethodId());
            cm.put("brand", c.getCardBrand());
            cm.put("last4", c.getCardLast4());
            cm.put("expMonth", c.getExpMonth());
            cm.put("expYear", c.getExpYear());
            cm.put("cardType", c.getCardType());
            cm.put("nickname", c.getNickname());
            cm.put("isDefault", c.isDefault());
            cards.add(cm);
        }

        List<Map<String, Object>> upis = new ArrayList<>();
        for (SavedUpi u : upiRepo.findByUserOrderByIsDefaultDescCreatedAtDesc(fresh)) {
            Map<String, Object> um = new HashMap<>();
            um.put("id", u.getId());
            um.put("vpa", u.getVpa());
            um.put("nickname", u.getNickname());
            um.put("isDefault", u.isDefault());
            upis.add(um);
        }

        Map<String, Object> m = new HashMap<>();
        m.put("id", fresh.getId());
        m.put("email", fresh.getEmail());
        m.put("name", fresh.getName());
        m.put("phone", fresh.getPhone());
        m.put("stripeCustomerId", fresh.getStripeCustomerId());
        m.put("savedCards", cards);
        m.put("savedUpis", upis);
        return m;
    }
}
