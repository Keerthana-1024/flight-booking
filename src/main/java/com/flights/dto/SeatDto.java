package com.flights.dto;

import lombok.Data;
import java.util.List;

@Data
public class SeatDto {
    private String seatNumber;
    private int row;
    private String col;
    private String cabin;     // BUSINESS or ECONOMY
    private String status;    // AVAILABLE, OCCUPIED, BLOCKED, LOCKED
    private boolean isExitRow;
    private String extraCost; // e.g. "₹1,200" or null
    private Long lockedByUserId;   // if locked or booked
    private Integer lockSecondsLeft; // countdown
    private String bookingReference; // set when status=BOOKED_BY_YOU
}
