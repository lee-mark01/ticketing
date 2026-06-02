package com.ticketing.booking.service;

import com.ticketing.booking.dto.ConfirmResponse;
import com.ticketing.booking.dto.HoldResponse;
import com.ticketing.booking.entity.ReservationStatus;
import com.ticketing.booking.repository.PaymentRepository;
import com.ticketing.booking.repository.ReservationItemRepository;
import com.ticketing.booking.repository.ReservationRepository;
import com.ticketing.common.exception.BusinessException;
import com.ticketing.event.entity.Event;
import com.ticketing.event.entity.EventSeat;
import com.ticketing.event.entity.EventSeatStatus;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.event.repository.EventSeatRepository;
import com.ticketing.user.entity.User;
import com.ticketing.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ReservationServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ReservationService reservationService;
    @Autowired ReservationRepository reservationRepository;
    @Autowired ReservationItemRepository reservationItemRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired EventRepository eventRepository;
    @Autowired EventSeatRepository eventSeatRepository;
    @Autowired UserRepository userRepository;
    @Autowired com.ticketing.booking.scheduler.HoldExpiryScheduler holdExpiryScheduler;

    private Long userId;
    private Long otherUserId;
    private Long eventId;
    private int initialAvailableSeatCount;

    @BeforeEach
    void setUp() {
        // beforePaymentHook 초기화 (AOP 프록시 뒤의 실제 빈에 설정)
        getTargetService().setBeforePaymentHook(null);

        // 테스트 사용자 생성 (이미 있으면 재사용)
        User user1 = userRepository.findByEmail("test1@test.com")
                .orElseGet(() -> userRepository.save(User.create("test1@test.com", "hashed", "테스트1")));
        User user2 = userRepository.findByEmail("test2@test.com")
                .orElseGet(() -> userRepository.save(User.create("test2@test.com", "hashed", "테스트2")));
        userId = user1.getId();
        otherUserId = user2.getId();

        // 시드 데이터의 첫 번째 이벤트 사용
        eventId = 1L;
        Event event = eventRepository.findById(eventId).orElseThrow();
        initialAvailableSeatCount = event.getAvailableSeatCount();
    }

    @Test
    @DisplayName("HOLD 성공: 좌석 AVAILABLE→HELD, reservation PENDING, items 생성, availableSeatCount 감소")
    void holdSeats_success() {
        // given
        List<Long> seatIds = findAvailableSeatIds(eventId, 2);

        // when
        HoldResponse response = reservationService.holdSeats(eventId, seatIds, userId);

        // then
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.heldSeats()).hasSize(2);
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now());

        for (Long seatId : seatIds) {
            EventSeat seat = eventSeatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getStatus()).isEqualTo(EventSeatStatus.HELD);
        }

        assertThat(reservationItemRepository.findByReservationId(response.reservationId())).hasSize(2);

        Event event = eventRepository.findById(eventId).orElseThrow();
        assertThat(event.getAvailableSeatCount()).isEqualTo(initialAvailableSeatCount - 2);
    }

    @Test
    @DisplayName("HOLD 실패: 이미 점유된 좌석 포함 시 전체 실패, 일부만 HELD 안 됨, availableSeatCount 변화 없음")
    void holdSeats_failWhenSeatAlreadyHeld() {
        // given: 좌석 1개를 먼저 HOLD
        List<Long> twoSeats = findAvailableSeatIds(eventId, 2);
        Long alreadyHeldSeatId = twoSeats.get(0);
        Long anotherSeatId = twoSeats.get(1);
        reservationService.holdSeats(eventId, List.of(alreadyHeldSeatId), userId);

        int countAfterFirstHold = eventRepository.findById(eventId).orElseThrow().getAvailableSeatCount();

        // when & then
        assertThatThrownBy(() ->
                reservationService.holdSeats(eventId, List.of(alreadyHeldSeatId, anotherSeatId), userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("SEAT_NOT_AVAILABLE"));

        // 두 번째 좌석은 AVAILABLE 유지 (전체 롤백)
        EventSeat secondSeat = eventSeatRepository.findById(anotherSeatId).orElseThrow();
        assertThat(secondSeat.getStatus()).isEqualTo(EventSeatStatus.AVAILABLE);

        // availableSeatCount 변화 없음
        Event event = eventRepository.findById(eventId).orElseThrow();
        assertThat(event.getAvailableSeatCount()).isEqualTo(countAfterFirstHold);
    }

    @Test
    @DisplayName("CONFIRM 성공: reservation CONFIRMED, 좌석 SOLD, payment 생성, availableSeatCount 변화 없음")
    void confirmReservation_success() {
        // given
        List<Long> seatIds = findAvailableSeatIds(eventId, 1);
        HoldResponse holdResponse = reservationService.holdSeats(eventId, seatIds, userId);
        int countAfterHold = eventRepository.findById(eventId).orElseThrow().getAvailableSeatCount();

        // when
        ConfirmResponse response = reservationService.confirmReservation(
                holdResponse.reservationId(), userId);

        // then
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.payment().status()).isEqualTo("PAID");
        assertThat(response.payment().amount()).isGreaterThan(0);

        for (Long seatId : seatIds) {
            EventSeat seat = eventSeatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getStatus()).isEqualTo(EventSeatStatus.SOLD);
        }

        // availableSeatCount 변화 없음
        Event event = eventRepository.findById(eventId).orElseThrow();
        assertThat(event.getAvailableSeatCount()).isEqualTo(countAfterHold);
    }

    @Test
    @DisplayName("CONFIRM 실패(롤백): seat.sell() 이후 예외 시 reservation PENDING, 좌석 HELD 유지, payment 미생성, availableSeatCount 변화 없음")
    void confirmReservation_rollbackOnFailure() {
        // given
        List<Long> seatIds = findAvailableSeatIds(eventId, 1);
        HoldResponse holdResponse = reservationService.holdSeats(eventId, seatIds, userId);
        int countAfterHold = eventRepository.findById(eventId).orElseThrow().getAvailableSeatCount();
        long paymentCountBefore = paymentRepository.count();

        // seat.sell() 이후, Payment 생성 직전에 예외 (AOP 프록시 뒤의 실제 빈에 설정)
        getTargetService().setBeforePaymentHook(() -> {
            throw new RuntimeException("모의 결제 실패");
        });

        // when & then
        assertThatThrownBy(() ->
                reservationService.confirmReservation(holdResponse.reservationId(), userId))
                .isInstanceOf(RuntimeException.class);

        // 좌석은 HELD 유지 (롤백됨)
        for (Long seatId : seatIds) {
            EventSeat seat = eventSeatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getStatus()).isEqualTo(EventSeatStatus.HELD);
        }

        // reservation PENDING 유지
        assertThat(reservationRepository.findById(holdResponse.reservationId()).orElseThrow()
                .getStatus()).isEqualTo(ReservationStatus.PENDING);

        // payment 미생성
        assertThat(paymentRepository.count()).isEqualTo(paymentCountBefore);

        // availableSeatCount 변화 없음
        Event event = eventRepository.findById(eventId).orElseThrow();
        assertThat(event.getAvailableSeatCount()).isEqualTo(countAfterHold);
    }

    @Test
    @DisplayName("만료된 HOLD 결제 실패: expires_at이 지난 reservation은 confirm 불가")
    void confirmReservation_failWhenExpired() {
        // given
        List<Long> seatIds = findAvailableSeatIds(eventId, 1);
        HoldResponse holdResponse = reservationService.holdSeats(eventId, seatIds, userId);

        forceExpireReservation(holdResponse.reservationId());

        // when & then
        assertThatThrownBy(() ->
                reservationService.confirmReservation(holdResponse.reservationId(), userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RESERVATION_EXPIRED"));
    }

    @Test
    @DisplayName("만료 스케줄러: PENDING+expired → EXPIRED 처리, HELD→AVAILABLE, availableSeatCount 증가")
    void holdExpiryScheduler_expiresAndReleasesSeats() {
        // given
        List<Long> seatIds = findAvailableSeatIds(eventId, 2);
        HoldResponse holdResponse = reservationService.holdSeats(eventId, seatIds, userId);
        int countAfterHold = eventRepository.findById(eventId).orElseThrow().getAvailableSeatCount();

        forceExpireReservation(holdResponse.reservationId());

        // when
        holdExpiryScheduler.expireHolds();

        // then
        var expiredReservation = reservationRepository.findById(holdResponse.reservationId()).orElseThrow();
        assertThat(expiredReservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        for (Long seatId : seatIds) {
            EventSeat seat = eventSeatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getStatus()).isEqualTo(EventSeatStatus.AVAILABLE);
        }

        Event event = eventRepository.findById(eventId).orElseThrow();
        assertThat(event.getAvailableSeatCount()).isEqualTo(countAfterHold + 2);
    }

    @Test
    @DisplayName("사용자 소유권: 다른 사용자의 reservation은 cancel/confirm 불가")
    void ownershipCheck_failsForDifferentUser() {
        // given
        List<Long> seatIds = findAvailableSeatIds(eventId, 1);
        HoldResponse holdResponse = reservationService.holdSeats(eventId, seatIds, userId);

        // when & then: cancel
        assertThatThrownBy(() ->
                reservationService.cancelHold(holdResponse.reservationId(), otherUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RESERVATION_NOT_FOUND"));

        // when & then: confirm
        assertThatThrownBy(() ->
                reservationService.confirmReservation(holdResponse.reservationId(), otherUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RESERVATION_NOT_FOUND"));
    }

    // === Helper ===

    private List<Long> findAvailableSeatIds(Long eventId, int count) {
        return eventSeatRepository.findAll().stream()
                .filter(s -> s.getEvent().getId().equals(eventId))
                .filter(s -> s.getStatus() == EventSeatStatus.AVAILABLE)
                .limit(count)
                .map(EventSeat::getId)
                .toList();
    }

    private ReservationService getTargetService() {
        return AopTestUtils.getTargetObject(reservationService);
    }

    private void forceExpireReservation(Long reservationId) {
        try {
            var reservation = reservationRepository.findById(reservationId).orElseThrow();
            var field = com.ticketing.booking.entity.Reservation.class.getDeclaredField("expiresAt");
            field.setAccessible(true);
            field.set(reservation, LocalDateTime.now().minusMinutes(10));
            reservationRepository.save(reservation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
