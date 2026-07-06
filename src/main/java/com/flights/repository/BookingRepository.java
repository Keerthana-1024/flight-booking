package com.flights.repository;

import com.flights.model.Booking;
import com.flights.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserOrderByCreatedAtDesc(User user);
    Optional<Booking> findByBookingReference(String reference);
    List<Booking> findByFlightIdAndStatus(String flightId, String status);
    Optional<Booking> findByIdAndUser(Long id, User user);
}
