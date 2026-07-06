package com.flights.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * SeatLockService uses Redis to lock seats for 3 minutes (180 seconds).
 * Key format: seat_lock:{flightId}:{seatNumber}
 * Value: userId
 */
@Service
public class SeatLockService {

    private static final long LOCK_TTL_SECONDS = 180L; // 3 minutes
    private static final String KEY_PREFIX = "seat_lock:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * Attempt to lock a seat for a user.
     * Returns true if lock acquired, false if already locked by another user.
     */
    public boolean lockSeat(String flightId, String seatNumber, Long userId) {
        String key = buildKey(flightId, seatNumber);
        String value = String.valueOf(userId);

        // SET key value NX EX 180 (atomic: only set if not exists)
        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(LOCK_TTL_SECONDS));

        if (Boolean.TRUE.equals(set)) {
            return true; // lock acquired
        }

        // Check if already locked by this same user — refresh TTL
        String existing = redisTemplate.opsForValue().get(key);
        if (value.equals(existing)) {
            redisTemplate.expire(key, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            return true;
        }

        return false; // locked by someone else
    }

    /**
     * Unlock a seat. Only the owning user can unlock.
     */
    public boolean unlockSeat(String flightId, String seatNumber, Long userId) {
        String key = buildKey(flightId, seatNumber);
        String existing = redisTemplate.opsForValue().get(key);
        if (String.valueOf(userId).equals(existing)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }

    /**
     * Check lock status for a seat.
     * Returns null if not locked, userId string if locked.
     */
    public String getLockOwner(String flightId, String seatNumber) {
        return redisTemplate.opsForValue().get(buildKey(flightId, seatNumber));
    }

    /**
     * Returns seconds left on the lock, or -1 if not locked.
     */
    public long getLockTtl(String flightId, String seatNumber) {
        Long ttl = redisTemplate.getExpire(buildKey(flightId, seatNumber), TimeUnit.SECONDS);
        return ttl != null ? ttl : -1;
    }

    /**
     * Verify all given seats are still locked by the user (used at payment time).
     */
    public boolean verifyAllLockedByUser(String flightId, String[] seatNumbers, Long userId) {
        String userIdStr = String.valueOf(userId);
        for (String seat : seatNumbers) {
            String owner = getLockOwner(flightId, seat.trim());
            if (!userIdStr.equals(owner)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Release all locks for a user on a given flight.
     */
    public void releaseAll(String flightId, String[] seatNumbers, Long userId) {
        for (String seat : seatNumbers) {
            unlockSeat(flightId, seat.trim(), userId);
        }
    }

    private String buildKey(String flightId, String seatNumber) {
        return KEY_PREFIX + flightId + ":" + seatNumber.toUpperCase();
    }
}
