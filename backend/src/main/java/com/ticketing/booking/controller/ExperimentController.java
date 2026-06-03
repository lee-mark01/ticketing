package com.ticketing.booking.controller;

import com.ticketing.booking.service.ConcurrencyExperimentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * ⚠️ experiment profile 전용 — 운영 사용 금지.
 *
 * 동시성 비교 실험 및 k6 부하 테스트용 endpoint.
 * JWT 없이 userId를 파라미터로 직접 받는다.
 */
@RestController
@RequestMapping("/api/experiment")
@Profile("experiment")
@RequiredArgsConstructor
public class ExperimentController {

    private final ConcurrencyExperimentService experimentService;
    private final JdbcTemplate jdbcTemplate;

    @PostMapping("/events/{eventId}/holds")
    public ResponseEntity<Void> holdSeat(
            @PathVariable Long eventId,
            @RequestParam String strategy,
            @RequestParam Long userId,
            @RequestParam Long seatId) {

        switch (strategy.toUpperCase()) {
            case "NAIVE" -> experimentService.holdSeatsNaive(eventId, seatId, userId);
            case "PESSIMISTIC" -> experimentService.holdSeatsPessimistic(eventId, seatId, userId);
            case "OPTIMISTIC" -> experimentService.holdSeatsOptimistic(eventId, seatId, userId);
            default -> throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@RequestParam Long eventId) {
        // FK 제약 고려 삭제 순서
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM reservation_items");
        jdbcTemplate.update("DELETE FROM reservations");
        jdbcTemplate.update(
                "UPDATE event_seats SET status = 'AVAILABLE', version = 0 WHERE event_id = ?",
                eventId);
        jdbcTemplate.update("""
                UPDATE events SET available_seat_count =
                    (SELECT COUNT(*) FROM event_seats WHERE event_id = ?)
                WHERE id = ?
                """, eventId, eventId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/seed-users")
    public ResponseEntity<Void> seedUsers(@RequestParam(defaultValue = "200") int count) {
        for (int i = 1; i <= count; i++) {
            String email = "experiment" + i + "@test.com";
            jdbcTemplate.update("""
                    INSERT INTO users (email, password_hash, name, created_at)
                    VALUES (?, 'hashed', ?, NOW())
                    ON CONFLICT (email) DO NOTHING
                    """, email, "실험사용자" + i);
        }
        return ResponseEntity.ok().build();
    }
}
