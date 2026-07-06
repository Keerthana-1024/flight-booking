package com.flights.repository;

import com.flights.model.SavedUpi;
import com.flights.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SavedUpiRepository extends JpaRepository<SavedUpi, Long> {
    List<SavedUpi> findByUserOrderByIsDefaultDescCreatedAtDesc(User user);
    void deleteByIdAndUser(Long id, User user);
    boolean existsByUserAndVpa(User user, String vpa);
}
