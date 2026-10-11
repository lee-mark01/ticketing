package com.ticketing.event.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventSeatRepositoryQueryTest {

    @Test
    @DisplayName("다중 좌석 비관적 락 쿼리는 ID 오름차순으로 잠근다")
    void pessimisticLockQueries_orderById() throws NoSuchMethodException {
        Query byIds = EventSeatRepository.class
                .getMethod("findAllByIdWithLock", List.class)
                .getAnnotation(Query.class);
        Query byEventAndIds = EventSeatRepository.class
                .getMethod("findAllByEventIdAndIdInWithLock", Long.class, List.class)
                .getAnnotation(Query.class);

        assertThat(byIds.value()).containsIgnoringCase("ORDER BY es.id");
        assertThat(byEventAndIds.value()).containsIgnoringCase("ORDER BY es.id");
    }
}
