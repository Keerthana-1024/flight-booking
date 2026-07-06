package com.flights.repository;

import com.flights.model.SavedCard;
import com.flights.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SavedCardRepository extends JpaRepository<SavedCard, Long> {
    List<SavedCard> findByUserOrderByIsDefaultDescCreatedAtDesc(User user);
    void deleteByIdAndUser(Long id, User user);
}
