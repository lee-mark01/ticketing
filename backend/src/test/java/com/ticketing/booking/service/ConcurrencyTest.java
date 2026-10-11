package com.ticketing.booking.service;

import com.ticketing.booking.dto.HoldResponse;
import com.ticketing.booking.entity.ReservationStatus;
import com.ticketing.booking.repository.ReservationRepository;
import com.ticketing.booking.scheduler.HoldExpiryScheduler;
import com.ticketing.common.exception.BusinessException;
import com.ticketing.event.entity.EventSeat;
import com.ticketing.event.entity.EventSeatStatus;
import com.ticketing.event.repository.EventSeatRepository;
import com.ticketing.user.entity.User;
import com.ticketing.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic concurrency test — critical interleaving을 결정적으로 재현하는 correctness test.
 *
 * 이 테스트는 실제 운영 부하 테스트가 아니라,
 * 동시성 문제가 발생 가능한 최악의 타이밍을 실험실에서 고정해 재현하는 테스트이다.
 * 실제 운영 환경에 가까운 성능 비교는 별도의 k6 부하 테스트에서 barrier 없이 수행한다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ConcurrencyExperimentService experimentService;
    @Autowired ReservationService reservationService;
    @Autowired HoldExpiryScheduler holdExpiryScheduler;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ReservationRepository reservationRepository;
    @Autowired EventSeatRepository eventSeatRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final int THREAD_COUNT = 10;
    private static final Long EVENT_ID = 1L;

    private List<Long> userIds;

    @BeforeEach
    void setUp() {
        // 테스트 간 격리: 이전 테스트 데이터 정리
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM reservation_items");
        jdbcTemplate.update("DELETE FROM reservations");
        jdbcTemplate.update(
                "UPDATE event_seats SET status = 'AVAILABLE', version = 0 WHERE event_id = ?",
                EVENT_ID);
        jdbcTemplate.update("""
                UPDATE events SET available_seat_count =
                    (SELECT COUNT(*) FROM event_seats WHERE event_id = ?)
                WHERE id = ?
                """, EVENT_ID, EVENT_ID);

        // 테스트용 사용자 10명 생성
        userIds = new ArrayList<>();
        for (int i = 1; i <= THREAD_COUNT; i++) {
            String email = "concurrency" + i + "@test.com";
            String name = "동시성테스트" + i;
            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> userRepository.save(
                            User.create(email, "hashed", name)));
            userIds.add(user.getId());
        }
    }

    @AfterEach
    void tearDown() {
        // readBarrier 초기화 — 이전 테스트의 barrier가 다음 테스트에 남지 않도록
        getTargetService().setReadBarrier(null);
        getTargetReservationService().setBeforeStatusTransitionHook(null);
        ReflectionTestUtils.setField(getTargetExpiryScheduler(), "beforeStatusTransitionHook", null);
    }

    @Test
    @DisplayName("NAIVE: 같은 좌석 1개에 10명 동시 HOLD → oversell 발생 (active reservation_item ≥ 2)")
    void naive_oversell() throws InterruptedException {
        Long seatId = findAvailableSeatId();
        CyclicBarrier readBarrier = new CyclicBarrier(THREAD_COUNT);
        getTargetService().setReadBarrier(readBarrier);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long uid = userIds.get(i);
            executor.submit(() -> {
                try {
                    experimentService.holdSeatsNaive(EVENT_ID, seatId, uid);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // oversell 판정: active reservation_item 수
        Long activeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reservation_items ri
                JOIN reservations r ON r.id = ri.reservation_id
                WHERE ri.event_seat_id = ? AND r.status IN ('PENDING', 'CONFIRMED')
                """, Long.class, seatId);

        System.out.println("[NAIVE] 성공: " + successCount.get()
                + ", 실패: " + failCount.get()
                + ", active reservation_items: " + activeCount);

        // NAIVE는 보호가 없으므로 active reservation_item ≥ 2 (oversell 재현)
        assertThat(activeCount).isGreaterThanOrEqualTo(2)
                .withFailMessage("NAIVE에서 oversell이 재현되지 않았습니다. "
                        + "성공: %d, active items: %d", successCount.get(), activeCount);
    }

    @Test
    @DisplayName("PESSIMISTIC: 같은 좌석 1개에 10명 동시 HOLD → 정확히 1건 성공, active reservation_item 1건")
    void pessimistic_exactlyOneSuccess() throws InterruptedException {
        Long seatId = findAvailableSeatId();
        // PESSIMISTIC은 readBarrier 사용하지 않음 — FOR UPDATE가 자연스럽게 직렬화
        // startLatch로 동시 시작만 맞춤

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long uid = userIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await(10, TimeUnit.SECONDS);
                    experimentService.holdSeatsPessimistic(EVENT_ID, seatId, uid);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 동시 출발
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        Long activeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reservation_items ri
                JOIN reservations r ON r.id = ri.reservation_id
                WHERE ri.event_seat_id = ? AND r.status IN ('PENDING', 'CONFIRMED')
                """, Long.class, seatId);

        System.out.println("[PESSIMISTIC] 성공: " + successCount.get()
                + ", 실패: " + failCount.get()
                + ", active reservation_items: " + activeCount);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    @DisplayName("OPTIMISTIC: 같은 좌석 1개에 10명 동시 HOLD → 정확히 1건 성공, version conflict 발생")
    void optimistic_exactlyOneSuccess() throws InterruptedException {
        Long seatId = findAvailableSeatId();
        CyclicBarrier readBarrier = new CyclicBarrier(THREAD_COUNT);
        getTargetService().setReadBarrier(readBarrier);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        List<String> failReasons = new CopyOnWriteArrayList<>();
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long uid = userIds.get(i);
            executor.submit(() -> {
                try {
                    experimentService.holdSeatsOptimistic(EVENT_ID, seatId, uid);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    failReasons.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        Long activeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reservation_items ri
                JOIN reservations r ON r.id = ri.reservation_id
                WHERE ri.event_seat_id = ? AND r.status IN ('PENDING', 'CONFIRMED')
                """, Long.class, seatId);

        System.out.println("[OPTIMISTIC] 성공: " + successCount.get()
                + ", 실패: " + failCount.get()
                + ", active reservation_items: " + activeCount);
        System.out.println("[OPTIMISTIC] 실패 사유: " + failReasons);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    @DisplayName("PESSIMISTIC 다중 좌석 묶음: 좌석 3개를 10명 동시 HOLD → 1명 성공, items 3건, 나머지 전체 실패")
    void pessimistic_multiSeatBundle() throws InterruptedException {
        List<Long> seatIds = findAvailableSeatIds(3);
        // PESSIMISTIC — startLatch만 사용

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long uid = userIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await(10, TimeUnit.SECONDS);
                    experimentService.holdSeatsPessimisticMulti(EVENT_ID, seatIds, uid);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // 성공한 reservation 수
        Long activeReservations = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT r.id) FROM reservations r
                WHERE r.event_id = ? AND r.status IN ('PENDING', 'CONFIRMED')
                """, Long.class, EVENT_ID);

        // 각 좌석별 active reservation_item 수
        Long totalActiveItems = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reservation_items ri
                JOIN reservations r ON r.id = ri.reservation_id
                WHERE r.event_id = ? AND r.status IN ('PENDING', 'CONFIRMED')
                """, Long.class, EVENT_ID);

        System.out.println("[PESSIMISTIC MULTI] 성공: " + successCount.get()
                + ", 실패: " + failCount.get()
                + ", active reservations: " + activeReservations
                + ", active items: " + totalActiveItems);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(activeReservations).isEqualTo(1);
        assertThat(totalActiveItems).isEqualTo(3);
    }

    @Test
    @DisplayName("서로 다른 좌석 10개 동시 HOLD: availableSeatCount는 실제 AVAILABLE 좌석 수와 일치")
    void concurrentDifferentSeats_keepsAvailableSeatCountConsistent() throws InterruptedException {
        List<Long> seatIds = findAvailableSeatIds(THREAD_COUNT);
        CyclicBarrier startBarrier = new CyclicBarrier(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        List<String> failReasons = new CopyOnWriteArrayList<>();
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long seatId = seatIds.get(i);
            final Long userId = userIds.get(i);
            executor.submit(() -> {
                try {
                    startBarrier.await(10, TimeUnit.SECONDS);
                    reservationService.holdSeats(EVENT_ID, List.of(seatId), userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failReasons.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        Integer storedCount = jdbcTemplate.queryForObject(
                "SELECT available_seat_count FROM events WHERE id = ?", Integer.class, EVENT_ID);
        Long actualCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_seats WHERE event_id = ? AND status = 'AVAILABLE'",
                Long.class, EVENT_ID);

        System.out.println("[AVAILABLE COUNT] 성공: " + successCount.get()
                + ", 실패 사유: " + failReasons
                + ", 저장 카운터: " + storedCount
                + ", 실제 AVAILABLE: " + actualCount);

        assertThat(successCount.get()).isEqualTo(THREAD_COUNT);
        assertThat(storedCount.longValue()).isEqualTo(actualCount);
    }

    @Test
    @DisplayName("confirm vs cancel: PENDING 조건부 전이로 정확히 하나만 성공")
    void confirmVsCancel_exactlyOneTransitionSucceeds() throws Exception {
        Long seatId = findAvailableSeatId();
        HoldResponse hold = reservationService.holdSeats(EVENT_ID, List.of(seatId), userIds.get(0));
        CyclicBarrier transitionBarrier = new CyclicBarrier(2);
        getTargetReservationService().setBeforeStatusTransitionHook(
                () -> awaitBarrier(transitionBarrier));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Throwable> confirm = executor.submit(() -> captureFailure(() ->
                reservationService.confirmReservation(hold.reservationId(), userIds.get(0))));
        Future<Throwable> cancel = executor.submit(() -> captureFailure(() ->
                reservationService.cancelHold(hold.reservationId(), userIds.get(0))));

        List<Throwable> failures = java.util.Arrays.asList(
                        confirm.get(30, TimeUnit.SECONDS), cancel.get(30, TimeUnit.SECONDS))
                .stream().filter(java.util.Objects::nonNull).toList();
        executor.shutdown();

        long successCount = 2L - failures.size();
        System.out.println("[CONFIRM VS CANCEL] 성공: " + successCount + ", 실패: " + describe(failures));

        assertThat(successCount).isEqualTo(1);
        assertThat(failures).singleElement().isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("confirm vs expire: PENDING 조건부 전이로 하나의 최종 상태만 반영")
    void confirmVsExpire_exactlyOneTransitionWins() throws Exception {
        Long seatId = findAvailableSeatId();
        HoldResponse hold = reservationService.holdSeats(EVENT_ID, List.of(seatId), userIds.get(0));
        jdbcTemplate.update("UPDATE reservations SET expires_at = now() + interval '1 second' WHERE id = ?",
                hold.reservationId());

        CyclicBarrier transitionBarrier = new CyclicBarrier(2);
        CountDownLatch confirmReachedTransition = new CountDownLatch(1);
        getTargetReservationService().setBeforeStatusTransitionHook(() -> {
            confirmReachedTransition.countDown();
            awaitBarrier(transitionBarrier);
        });
        ReflectionTestUtils.setField(getTargetExpiryScheduler(), "beforeStatusTransitionHook",
                (Runnable) () -> awaitBarrier(transitionBarrier));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Throwable> confirm = executor.submit(() -> captureFailure(() ->
                reservationService.confirmReservation(hold.reservationId(), userIds.get(0))));
        assertThat(confirmReachedTransition.await(10, TimeUnit.SECONDS)).isTrue();
        awaitReservationExpiry(hold.reservationId());
        Future<Throwable> expire = executor.submit(() -> captureFailure(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(
                        ignored -> ReflectionTestUtils.invokeMethod(
                                getTargetExpiryScheduler(),
                                "expireReservation",
                                reservationRepository.findById(hold.reservationId()).orElseThrow(),
                                LocalDateTime.now()))));

        List<Throwable> failures = java.util.Arrays.asList(
                        confirm.get(30, TimeUnit.SECONDS), expire.get(30, TimeUnit.SECONDS))
                .stream().filter(java.util.Objects::nonNull).toList();
        executor.shutdown();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM reservations WHERE id = ?", String.class, hold.reservationId());
        String seatStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM event_seats WHERE id = ?", String.class, seatId);
        Integer paymentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE reservation_id = ?", Integer.class, hold.reservationId());

        System.out.println("[CONFIRM VS EXPIRE] 최종 예매: " + status
                + ", 좌석: " + seatStatus
                + ", 결제: " + paymentCount
                + ", 실패: " + describe(failures));

        assertThat(failures).allMatch(BusinessException.class::isInstance);
        if (ReservationStatus.CONFIRMED.name().equals(status)) {
            assertThat(seatStatus).isEqualTo("SOLD");
            assertThat(paymentCount).isEqualTo(1);
        } else {
            assertThat(status).isEqualTo(ReservationStatus.EXPIRED.name());
            assertThat(seatStatus).isEqualTo("AVAILABLE");
            assertThat(paymentCount).isZero();
        }
    }

    // === Helper ===

    private ConcurrencyExperimentService getTargetService() {
        return AopTestUtils.getTargetObject(experimentService);
    }

    private ReservationService getTargetReservationService() {
        return AopTestUtils.getTargetObject(reservationService);
    }

    private HoldExpiryScheduler getTargetExpiryScheduler() {
        return AopTestUtils.getTargetObject(holdExpiryScheduler);
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Throwable captureFailure(ThrowingAction action) {
        try {
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private static List<String> describe(List<Throwable> failures) {
        return failures.stream()
                .map(failure -> failure.getClass().getSimpleName() + ": " + failure.getMessage())
                .toList();
    }

    private void awaitReservationExpiry(Long reservationId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Boolean expired = jdbcTemplate.queryForObject(
                    "SELECT expires_at < now() FROM reservations WHERE id = ?", Boolean.class, reservationId);
            if (Boolean.TRUE.equals(expired)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("예매 만료 시각을 5초 안에 지나지 못했습니다.");
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private Long findAvailableSeatId() {
        return eventSeatRepository.findAll().stream()
                .filter(s -> s.getEvent().getId().equals(EVENT_ID))
                .filter(s -> s.getStatus() == EventSeatStatus.AVAILABLE)
                .findFirst()
                .map(EventSeat::getId)
                .orElseThrow(() -> new IllegalStateException("AVAILABLE 좌석이 없습니다"));
    }

    private List<Long> findAvailableSeatIds(int count) {
        return eventSeatRepository.findAll().stream()
                .filter(s -> s.getEvent().getId().equals(EVENT_ID))
                .filter(s -> s.getStatus() == EventSeatStatus.AVAILABLE)
                .limit(count)
                .map(EventSeat::getId)
                .toList();
    }
}
