package com.flights.repository;

import com.flights.model.PaymentRecord;
import com.flights.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    List<PaymentRecord> findByUserOrderByCreatedAtDesc(User user);
    Optional<PaymentRecord> findByPaymentIntentId(String paymentIntentId);
}
