## Troubleshooting — @Transactional 롤백 테스트 예외 미발생

### 문제
CONFIRM 실패 롤백 테스트에서 `assertThatThrownBy()`가 실패했다.  
예상은 결제 직전 `RuntimeException`이 발생하는 것이었지만, 실제로는 예외가 발생하지 않아 confirm 로직이 정상 완료되었다.

### 원인
처음에는 JPA 1차 캐시 또는 롤백 후 조회 문제를 의심했지만, 실제 원인은 테스트용 `beforePaymentHook`이 실제 `ReservationService` target 객체에 주입되지 않은 것이었다.

`@Transactional`이 적용된 서비스는 Spring AOP 프록시를 통해 호출된다. 테스트에서 프록시 객체의 필드에 직접 hook을 설정했지만, 실제 메서드 실행은 프록시 뒤의 target 객체에서 수행되어 hook이 실행되지 않았다.

### 해결
Spring Test의 `AopTestUtils.getTargetObject(reservationService)`를 사용해 프록시 뒤의 실제 target 객체를 꺼낸 뒤, 해당 객체에 `beforePaymentHook`을 설정했다.

### 결과
CONFIRM 실패 테스트에서 seat.sell() 이후 Payment 생성 직전 `RuntimeException`이 정상 발생했고, 트랜잭션 롤백 결과를 검증했다.

검증 결과:
- reservation: `PENDING` 유지
- event_seat: `HELD` 유지
- payment: 미생성
- availableSeatCount: 변화 없음

### 배운 점
`@Transactional`은 단순 어노테이션이 아니라 Spring AOP 프록시를 통해 트랜잭션을 시작·커밋·롤백한다.  
따라서 테스트에서 프록시 객체와 실제 target 객체의 차이를 이해해야 하며, 롤백 테스트에서는 예외가 실제 트랜잭션 내부에서 발생하는지 먼저 확인해야 한다.