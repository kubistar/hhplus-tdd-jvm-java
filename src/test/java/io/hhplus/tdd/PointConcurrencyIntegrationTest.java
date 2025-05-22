package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PointConcurrencyIntegrationTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private UserPointTable userPointTable;

    @Autowired
    private PointHistoryTable pointHistoryTable;

    private final long TEST_USER_ID = 1L;
    private final int THREAD_COUNT = 10;
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

    @BeforeEach
    void setUp() {
        // 테스트 전 데이터 초기화
        userPointTable.insertOrUpdate(TEST_USER_ID, 0L);
    }

    @Test
    @DisplayName("동시에 여러 번 포인트 충전 시 정확한 금액이 누적되어야 한다")
    void concurrentChargeTest() throws InterruptedException {
        // given
        long chargeAmount = 1000L;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 동시에 시작하도록 대기
                    pointService.chargePoint(TEST_USER_ID, chargeAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("충전 실패: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 모든 스레드 동시 시작
        endLatch.await(10, TimeUnit.SECONDS); // 최대 10초 대기

        // then
        UserPoint finalPoint = pointService.getPoint(TEST_USER_ID);
        long expectedPoint = chargeAmount * THREAD_COUNT;

        assertThat(finalPoint.point()).isEqualTo(expectedPoint);
        assertThat(successCount.get()).isEqualTo(THREAD_COUNT);
        assertThat(failCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("동시에 여러 번 포인트 사용 시 정확한 금액이 차감되어야 한다")
    void concurrentUseTest() throws InterruptedException {
        // given
        long initialPoint = 10000L;
        long useAmount = 500L;
        userPointTable.insertOrUpdate(TEST_USER_ID, initialPoint);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    pointService.usePoint(TEST_USER_ID, useAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("사용 실패: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);

        // then
        UserPoint finalPoint = pointService.getPoint(TEST_USER_ID);
        long expectedPoint = initialPoint - (useAmount * THREAD_COUNT);

        assertThat(finalPoint.point()).isEqualTo(expectedPoint);
        assertThat(successCount.get()).isEqualTo(THREAD_COUNT);
        assertThat(failCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("잔액 부족 시 동시 사용 요청에서 일부만 성공해야 한다")
    void concurrentUseWithInsufficientBalanceTest() throws InterruptedException {
        // given
        long initialPoint = 3000L;
        long useAmount = 1000L;
        userPointTable.insertOrUpdate(TEST_USER_ID, initialPoint);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    pointService.usePoint(TEST_USER_ID, useAmount);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("예상치 못한 오류: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);

        // then
        UserPoint finalPoint = pointService.getPoint(TEST_USER_ID);
        int expectedSuccessCount = (int) (initialPoint / useAmount); // 3번만 성공해야 함

        assertThat(successCount.get()).isEqualTo(expectedSuccessCount);
        assertThat(failCount.get()).isEqualTo(THREAD_COUNT - expectedSuccessCount);
        assertThat(finalPoint.point()).isEqualTo(initialPoint - (useAmount * expectedSuccessCount));
    }

    @Test
    @DisplayName("충전과 사용이 동시에 발생할 때 데이터 일관성이 보장되어야 한다")
    void concurrentChargeAndUseTest() throws InterruptedException {
        // given
        long initialPoint = 5000L;
        long chargeAmount = 1000L;
        long useAmount = 800L;
        userPointTable.insertOrUpdate(TEST_USER_ID, initialPoint);

        int chargeThreadCount = 5;
        int useThreadCount = 5;
        int totalThreadCount = chargeThreadCount + useThreadCount;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreadCount);
        AtomicInteger chargeSuccessCount = new AtomicInteger(0);
        AtomicInteger useSuccessCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 충전 스레드들
        for (int i = 0; i < chargeThreadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    pointService.chargePoint(TEST_USER_ID, chargeAmount);
                    chargeSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("충전 실패: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // when - 사용 스레드들
        for (int i = 0; i < useThreadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    pointService.usePoint(TEST_USER_ID, useAmount);
                    useSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("사용 실패: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);

        // then
        UserPoint finalPoint = pointService.getPoint(TEST_USER_ID);
        long expectedPoint = initialPoint +
                (chargeAmount * chargeSuccessCount.get()) -
                (useAmount * useSuccessCount.get());

        assertThat(finalPoint.point()).isEqualTo(expectedPoint);
        assertThat(finalPoint.point()).isGreaterThanOrEqualTo(0L); // 음수가 되면 안됨

        // 총 성공한 트랜잭션 수 확인
        int totalSuccessCount = chargeSuccessCount.get() + useSuccessCount.get();
        assertThat(totalSuccessCount + failCount.get()).isEqualTo(totalThreadCount);
    }

    @Test
    @DisplayName("최대 보유 포인트 제한 동시성 테스트")
    void concurrentChargeWithMaxPointLimitTest() throws InterruptedException {
        // given
        long maxPoint = 100_000_000L;
        long initialPoint = maxPoint - 5000L; // 최대값에 가깝게 설정
        long chargeAmount = 1000L;
        userPointTable.insertOrUpdate(TEST_USER_ID, initialPoint);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    pointService.chargePoint(TEST_USER_ID, chargeAmount);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().contains("최대 보유 포인트")) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("예상치 못한 오류: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);

        // then
        UserPoint finalPoint = pointService.getPoint(TEST_USER_ID);
        assertThat(finalPoint.point()).isLessThanOrEqualTo(maxPoint);

        // 성공한 충전 횟수만큼 포인트가 증가해야 함
        long expectedPoint = initialPoint + (chargeAmount * successCount.get());
        assertThat(finalPoint.point()).isEqualTo(expectedPoint);

        // 일부는 성공하고 일부는 실패해야 함
        assertThat(successCount.get()).isGreaterThan(0);
        assertThat(failCount.get()).isGreaterThan(0);
        assertThat(successCount.get() + failCount.get()).isEqualTo(THREAD_COUNT);
    }

    @Test
    @DisplayName("동시 요청 시 히스토리가 정확히 기록되어야 한다")
    void concurrentTransactionHistoryTest() throws InterruptedException {
        // given
        long initialPoint = 10000L;
        long chargeAmount = 500L;
        long useAmount = 300L;
        userPointTable.insertOrUpdate(TEST_USER_ID, initialPoint);

        int chargeCount = 3;
        int useCount = 2;
        int totalCount = chargeCount + useCount;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalCount);

        // when
        // 충전 요청들
        for (int i = 0; i < chargeCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    pointService.chargePoint(TEST_USER_ID, chargeAmount);
                } catch (Exception e) {
                    System.err.println("충전 실패: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 사용 요청들
        for (int i = 0; i < useCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    pointService.usePoint(TEST_USER_ID, useAmount);
                } catch (Exception e) {
                    System.err.println("사용 실패: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);

        // then
        List<PointHistory> histories = pointService.getHistories(TEST_USER_ID);

        // 히스토리 개수 확인 (초기 데이터는 제외하고 새로 생성된 것만)
        long chargeHistoryCount = histories.stream()
                .filter(h -> h.type() == TransactionType.CHARGE)
                .count();
        long useHistoryCount = histories.stream()
                .filter(h -> h.type() == TransactionType.USE)
                .count();

        assertThat(chargeHistoryCount).isEqualTo(chargeCount);
        assertThat(useHistoryCount).isEqualTo(useCount);

        // 최종 포인트와 히스토리의 합계가 일치하는지 확인
        UserPoint finalPoint = pointService.getPoint(TEST_USER_ID);
        long expectedFinalPoint = initialPoint +
                (chargeAmount * chargeCount) -
                (useAmount * useCount);
        assertThat(finalPoint.point()).isEqualTo(expectedFinalPoint);
    }
}
