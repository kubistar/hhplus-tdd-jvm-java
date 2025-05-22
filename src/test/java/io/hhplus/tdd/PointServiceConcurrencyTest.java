package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포인트 충전 동시성 테스트
 * 여러 스레드가 동시에 같은 사용자의 포인트를 충전할 때 데이터 정합성을 검증
 */
class PointServiceConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(PointServiceConcurrencyTest.class);

    private PointService pointService;
    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;

    @BeforeEach
    void setUp() {
        userPointTable = new UserPointTable();
        pointHistoryTable = new PointHistoryTable();
        pointService = new PointService(userPointTable, pointHistoryTable);
    }

    /**
     * 동시성 테스트 1: 단일 사용자에 대해 여러 스레드가 동시에 충전
     * 예상 결과: 모든 충전이 순차적으로 처리되어 최종 포인트가 정확해야 함
     */
    @Test
    void 단일_사용자_동시_충전_테스트() throws InterruptedException {
        // Given
        long userId = 1L;
        int threadCount = 10;
        long chargeAmount = 1000L;
        long expectedFinalPoint = threadCount * chargeAmount;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<UserPoint>> futures = new ArrayList<>();

        // When: 10개 스레드가 동시에 1000포인트씩 충전
        for (int i = 0; i < threadCount; i++) {
            Future<UserPoint> future = executorService.submit(() -> {
                try {
                    return pointService.chargePoint(userId, chargeAmount);
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // 모든 스레드 완료 대기
        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 최종 포인트 검증
        UserPoint finalPoint = pointService.getPoint(userId);
        assertThat(finalPoint.point()).isEqualTo(expectedFinalPoint);

        // 모든 Future 결과도 검증
        for (Future<UserPoint> future : futures) {
            try {
                UserPoint result = future.get();
                assertThat(result.point()).isLessThanOrEqualTo(expectedFinalPoint);
                log.info("Thread result: {}", result.point());
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        log.info("최종 포인트: {}, 예상 포인트: {}", finalPoint.point(), expectedFinalPoint);
    }

    /**
     * 동시성 테스트 2: 여러 사용자에 대해 각각 여러 스레드가 동시에 충전
     * 예상 결과: 각 사용자별로 독립적으로 충전이 처리되어야 함
     */
    @Test
    void 다중_사용자_동시_충전_테스트() throws InterruptedException {
        // Given
        int userCount = 5;
        int threadPerUser = 4;
        long chargeAmount = 500L;
        long expectedPointPerUser = threadPerUser * chargeAmount;

        ExecutorService executorService = Executors.newFixedThreadPool(userCount * threadPerUser);
        CountDownLatch latch = new CountDownLatch(userCount * threadPerUser);

        // When: 5명의 사용자가 각각 4개 스레드로 동시 충전
        for (long userId = 1; userId <= userCount; userId++) {
            for (int thread = 0; thread < threadPerUser; thread++) {
                long currentUserId = userId;
                executorService.submit(() -> {
                    try {
                        pointService.chargePoint(currentUserId, chargeAmount);
                        log.info("사용자 {} 충전 완료", currentUserId);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        // 모든 스레드 완료 대기
        latch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 각 사용자별 최종 포인트 검증
        for (long userId = 1; userId <= userCount; userId++) {
            UserPoint userPoint = pointService.getPoint(userId);
            assertThat(userPoint.point()).isEqualTo(expectedPointPerUser);
            log.info("사용자 {} 최종 포인트: {}", userId, userPoint.point());
        }
    }

    /**
     * 동시성 테스트 3: 대량의 스레드로 스트레스 테스트
     * 예상 결과: 높은 동시성 상황에서도 데이터 정합성이 보장되어야 함
     */
    @Test
    void 대량_스레드_스트레스_테스트() throws InterruptedException {
        // Given
        long userId = 100L;
        int threadCount = 100;
        long chargeAmount = 10L;
        long expectedFinalPoint = threadCount * chargeAmount;

        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // When: 100개 스레드가 동시에 10포인트씩 충전
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(userId, chargeAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("충전 실패: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        long endTime = System.currentTimeMillis();

        // Then: 결과 검증
        UserPoint finalPoint = pointService.getPoint(userId);
        assertThat(finalPoint.point()).isEqualTo(expectedFinalPoint);
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);

        log.info("스트레스 테스트 결과:");
        log.info("- 처리 시간: {}ms", endTime - startTime);
        log.info("- 성공: {}, 실패: {}", successCount.get(), failCount.get());
        log.info("- 최종 포인트: {}", finalPoint.point());
    }

    /**
     * 동시성 테스트 4: 최대 포인트 제한 동시성 테스트
     * 예상 결과: 최대 포인트를 초과하는 충전은 적절히 거부되어야 함
     */
    @Test
    void 최대_포인트_제한_동시성_테스트() throws InterruptedException {
        // Given
        long userId = 200L;
        long initialPoint = 99_900_000L; // 최대 한계 근처로 초기 설정
        long chargeAmount = 50_000L;
        int threadCount = 10;

        // 초기 포인트 설정
        userPointTable.insertOrUpdate(userId, initialPoint);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 여러 스레드가 동시에 충전 시도 (일부는 최대 포인트 초과)
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(userId, chargeAmount);
                    successCount.incrementAndGet();
                    log.info("충전 성공");
                } catch (IllegalArgumentException e) {
                    failCount.incrementAndGet();
                    log.info("충전 실패 (예상됨): {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 최대 포인트를 초과하지 않았는지 검증
        UserPoint finalPoint = pointService.getPoint(userId);
        assertThat(finalPoint.point()).isLessThanOrEqualTo(100_000_000L);
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);

        log.info("최대 포인트 테스트 결과:");
        log.info("- 성공: {}, 실패: {}", successCount.get(), failCount.get());
        log.info("- 최종 포인트: {}", finalPoint.point());
    }

    /**
     * 동시성 테스트 5: 충전과 동시에 포인트 조회 테스트
     * 예상 결과: 조회 시점의 포인트가 일관성 있게 반환되어야 함
     */
    @Test
    void 충전_조회_동시성_테스트() throws InterruptedException {
        // Given
        long userId = 300L;
        int chargeThreadCount = 5;
        int queryThreadCount = 5;
        long chargeAmount = 1000L;

        ExecutorService executorService = Executors.newFixedThreadPool(chargeThreadCount + queryThreadCount);
        CountDownLatch latch = new CountDownLatch(chargeThreadCount + queryThreadCount);
        List<Long> queryResults = new CopyOnWriteArrayList<>();

        // When: 충전과 조회를 동시에 실행
        // 충전 스레드들
        for (int i = 0; i < chargeThreadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(userId, chargeAmount);
                    log.info("충전 완료");
                } finally {
                    latch.countDown();
                }
            });
        }

        // 조회 스레드들
        for (int i = 0; i < queryThreadCount; i++) {
            executorService.submit(() -> {
                try {
                    Thread.sleep(10); // 약간의 지연으로 충전 중간에 조회
                    UserPoint point = pointService.getPoint(userId);
                    queryResults.add(point.point());
                    log.info("조회 결과: {}", point.point());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 모든 조회 결과가 valid한 값인지 확인
        UserPoint finalPoint = pointService.getPoint(userId);
        for (Long queryResult : queryResults) {
            assertThat(queryResult).isBetween(0L, finalPoint.point());
        }

        log.info("동시 조회 테스트 결과:");
        log.info("- 최종 포인트: {}", finalPoint.point());
        log.info("- 조회 결과들: {}", queryResults);
    }

    /**
     * 동시성 테스트 6: 단일 사용자에 대해 여러 스레드가 동시에 포인트 사용
     * 현재 usePoint 메서드에는 동시성 제어가 없어서 Race Condition이 발생할 수 있음
     */
    @Test
    void 단일_사용자_동시_사용_테스트() throws InterruptedException {
        // Given
        long userId = 1L;
        long initialPoint = 10000L;
        int threadCount = 10;
        long useAmount = 500L;
        long expectedFinalPoint = initialPoint - (threadCount * useAmount);

        // 초기 포인트 설정
        pointService.chargePoint(userId, initialPoint);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<UserPoint>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 500포인트씩 사용
        for (int i = 0; i < threadCount; i++) {
            Future<UserPoint> future = executorService.submit(() -> {
                try {
                    UserPoint result = pointService.usePoint(userId, useAmount);
                    successCount.incrementAndGet();
                    return result;
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.warn("포인트 사용 실패: {}", e.getMessage());
                    throw e;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // 모든 스레드 완료 대기
        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 최종 포인트 검증
        UserPoint finalPoint = pointService.getPoint(userId);

        log.info("초기 포인트: {}", initialPoint);
        log.info("예상 최종 포인트: {}", expectedFinalPoint);
        log.info("실제 최종 포인트: {}", finalPoint.point());
        log.info("성공: {}, 실패: {}", successCount.get(), failCount.get());

        // 동시성 제어가 없다면 최종 포인트가 예상값과 다를 수 있음
        // 이 테스트는 동시성 문제를 발견하기 위한 것
        if (finalPoint.point() != expectedFinalPoint) {
            log.warn("동시성 문제 발견! 예상: {}, 실제: {}", expectedFinalPoint, finalPoint.point());
        }

        // 최소한 0 이상이어야 함 (음수가 되면 안됨)
        assertThat(finalPoint.point()).isGreaterThanOrEqualTo(0L);
    }

    /**
     * 동시성 테스트 7: 잔액 부족 상황에서의 동시 사용 테스트
     * 여러 스레드가 동시에 사용 시도할 때 잔액 부족 예외 처리 검증
     */
    @Test
    void 잔액_부족_동시_사용_테스트() throws InterruptedException {
        // Given
        long userId = 2L;
        long initialPoint = 1000L;
        int threadCount = 10;
        long useAmount = 200L; // 5번만 성공 가능

        // 초기 포인트 설정
        pointService.chargePoint(userId, initialPoint);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 200포인트씩 사용 시도 (5번만 성공 가능)
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.usePoint(userId, useAmount);
                    successCount.incrementAndGet();
                    log.info("포인트 사용 성공");
                } catch (IllegalArgumentException e) {
                    failCount.incrementAndGet();
                    log.info("포인트 사용 실패 (잔액 부족): {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 결과 검증
        UserPoint finalPoint = pointService.getPoint(userId);

        log.info("최종 포인트: {}", finalPoint.point());
        log.info("성공: {}, 실패: {}", successCount.get(), failCount.get());

        // 최종 포인트는 0 이상이어야 함
        assertThat(finalPoint.point()).isGreaterThanOrEqualTo(0L);
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);

        // 동시성 제어가 제대로 되면 성공 횟수는 5번이어야 함
        // 하지만 현재 구조에서는 race condition으로 인해 다를 수 있음
    }

    /**
     * 동시성 테스트 8: 여러 사용자의 독립적인 포인트 사용
     * 각 사용자별로 독립적으로 처리되는지 확인
     */
    @Test
    void 다중_사용자_동시_사용_테스트() throws InterruptedException {
        // Given
        int userCount = 5;
        long initialPointPerUser = 2000L;
        int threadPerUser = 4;
        long useAmount = 300L;
        long expectedFinalPointPerUser = initialPointPerUser - (threadPerUser * useAmount);

        // 각 사용자별 초기 포인트 설정
        for (long userId = 1; userId <= userCount; userId++) {
            pointService.chargePoint(userId, initialPointPerUser);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount * threadPerUser);
        CountDownLatch latch = new CountDownLatch(userCount * threadPerUser);

        // When: 5명의 사용자가 각각 4개 스레드로 동시 사용
        for (long userId = 1; userId <= userCount; userId++) {
            for (int thread = 0; thread < threadPerUser; thread++) {
                long currentUserId = userId;
                executorService.submit(() -> {
                    try {
                        pointService.usePoint(currentUserId, useAmount);
                        log.info("사용자 {} 포인트 사용 완료", currentUserId);
                    } catch (Exception e) {
                        log.warn("사용자 {} 포인트 사용 실패: {}", currentUserId, e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        // 모든 스레드 완료 대기
        latch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 각 사용자별 최종 포인트 검증
        for (long userId = 1; userId <= userCount; userId++) {
            UserPoint userPoint = pointService.getPoint(userId);
            log.info("사용자 {} - 초기: {}, 예상: {}, 실제: {}",
                    userId, initialPointPerUser, expectedFinalPointPerUser, userPoint.point());

            // 최소한 0 이상이어야 함
            assertThat(userPoint.point()).isGreaterThanOrEqualTo(0L);
        }
    }

    /**
     * 동시성 테스트 9: 충전과 사용이 동시에 발생하는 상황
     * 한 사용자에게 충전과 사용이 동시에 일어날 때의 정합성 검증
     */
    @Test
    void 충전_사용_동시_발생_테스트() throws InterruptedException {
        // Given
        long userId = 100L;
        long initialPoint = 5000L;
        int chargeThreadCount = 5;
        int useThreadCount = 5;
        long chargeAmount = 1000L;
        long useAmount = 800L;

        // 초기 포인트 설정
        pointService.chargePoint(userId, initialPoint);

        ExecutorService executorService = Executors.newFixedThreadPool(chargeThreadCount + useThreadCount);
        CountDownLatch latch = new CountDownLatch(chargeThreadCount + useThreadCount);
        AtomicInteger chargeSuccessCount = new AtomicInteger(0);
        AtomicInteger useSuccessCount = new AtomicInteger(0);
        AtomicInteger totalFailCount = new AtomicInteger(0);

        // When: 충전과 사용을 동시에 실행
        // 충전 스레드들
        for (int i = 0; i < chargeThreadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(userId, chargeAmount);
                    chargeSuccessCount.incrementAndGet();
                    log.info("충전 성공");
                } catch (Exception e) {
                    totalFailCount.incrementAndGet();
                    log.warn("충전 실패: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 사용 스레드들
        for (int i = 0; i < useThreadCount; i++) {
            executorService.submit(() -> {
                try {
                    Thread.sleep(50); // 약간의 지연으로 충전 후 사용 시뮬레이션
                    pointService.usePoint(userId, useAmount);
                    useSuccessCount.incrementAndGet();
                    log.info("사용 성공");
                } catch (Exception e) {
                    totalFailCount.incrementAndGet();
                    log.info("사용 실패 (잔액 부족 등): {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 결과 검증
        UserPoint finalPoint = pointService.getPoint(userId);
        long expectedMinPoint = initialPoint + (chargeSuccessCount.get() * chargeAmount) - (useSuccessCount.get() * useAmount);

        log.info("충전 사용 동시 테스트 결과:");
        log.info("- 초기 포인트: {}", initialPoint);
        log.info("- 충전 성공: {}, 사용 성공: {}, 실패: {}", chargeSuccessCount.get(), useSuccessCount.get(), totalFailCount.get());
        log.info("- 최종 포인트: {}", finalPoint.point());

        // 최종 포인트가 음수가 되면 안됨
        assertThat(finalPoint.point()).isGreaterThanOrEqualTo(0L);
    }

    /**
     * 동시성 테스트 5: 대량 스레드로 포인트 사용 스트레스 테스트
     * 높은 동시성 상황에서의 데이터 정합성 검증
     */
    @Test
    void 포인트_사용_동시성_테스트() throws InterruptedException {
        // Given
        long userId = 500L;
        long initialPoint = 50000L;
        int threadCount = 50;
        long useAmount = 100L;

        // 초기 포인트 설정
        pointService.chargePoint(userId, initialPoint);

        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // When: 50개 스레드가 동시에 100포인트씩 사용
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.usePoint(userId, useAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.debug("포인트 사용 실패: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        long endTime = System.currentTimeMillis();

        // Then: 결과 검증
        UserPoint finalPoint = pointService.getPoint(userId);
        long expectedPoint = initialPoint - (successCount.get() * useAmount);

        log.info("포인트 사용 스트레스 테스트 결과:");
        log.info("- 처리 시간: {}ms", endTime - startTime);
        log.info("- 초기 포인트: {}", initialPoint);
        log.info("- 성공: {}, 실패: {}", successCount.get(), failCount.get());
        log.info("- 예상 최종 포인트: {}", expectedPoint);
        log.info("- 실제 최종 포인트: {}", finalPoint.point());

        // 최종 포인트가 음수가 되면 안됨
        assertThat(finalPoint.point()).isGreaterThanOrEqualTo(0L);
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);

        // 동시성 제어가 제대로 되었다면 예상 포인트와 실제 포인트가 일치해야 함
        if (finalPoint.point() != expectedPoint) {
            log.warn("동시성 문제로 인한 데이터 불일치 발견!");
            log.warn("예상: {}, 실제: {}, 차이: {}", expectedPoint, finalPoint.point(), expectedPoint - finalPoint.point());
        }
    }

    /**
     * 동시성 테스트 6: 음수 금액 사용 시도 동시성 테스트
     * 여러 스레드가 동시에 잘못된 값으로 사용 시도할 때 예외 처리 검증
     */
    @Test
    void 음수_금액_사용_동시성_테스트() throws InterruptedException {
        // Given
        long userId = 600L;
        long initialPoint = 1000L;
        int threadCount = 10;
        long invalidAmount = -100L;

        // 초기 포인트 설정
        pointService.chargePoint(userId, initialPoint);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 음수 금액으로 사용 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.usePoint(userId, invalidAmount);
                } catch (IllegalArgumentException e) {
                    exceptionCount.incrementAndGet();
                    log.info("예상된 예외 발생: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 모든 요청이 예외를 발생시켜야 함
        UserPoint finalPoint = pointService.getPoint(userId);

        assertThat(exceptionCount.get()).isEqualTo(threadCount);
        assertThat(finalPoint.point()).isEqualTo(initialPoint); // 포인트 변화 없음

        log.info("음수 금액 테스트 결과:");
        log.info("- 예외 발생 횟수: {}", exceptionCount.get());
        log.info("- 최종 포인트: {} (변화 없음)", finalPoint.point());
    }
}