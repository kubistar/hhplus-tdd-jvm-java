package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class UsePointServiceTest {

    private PointService pointService;
    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;

    private static final Logger log = LoggerFactory.getLogger(UsePointServiceTest.class);

    @BeforeEach
    void setUp() {
        userPointTable = mock(UserPointTable.class);
        pointService = new PointService(userPointTable, pointHistoryTable);
    }

    @Test
    void 포인트_사용_성공() {
        // given
        long userId = 1L;
        long initialPoint = 10_000L;
        long useAmount = 3_000L;
        long expectedPoint = initialPoint - useAmount;

        log.info("테스트 시작 - userId: {}, initialPoint: {}, useAmount: {}", userId, initialPoint, useAmount);

        UserPoint current = new UserPoint(userId, initialPoint, System.currentTimeMillis());
        UserPoint updated = new UserPoint(userId, expectedPoint, System.currentTimeMillis());

        when(userPointTable.selectById(userId)).thenReturn(current);
        when(userPointTable.insertOrUpdate(userId, expectedPoint)).thenReturn(updated);

        // when
        UserPoint result = pointService.usePoint(userId, useAmount);

        // then
        log.info("포인트 사용 후 결과: {}", result);
        assertEquals(expectedPoint, result.point());
        verify(userPointTable).insertOrUpdate(userId, expectedPoint);
    }

    @Test
    void 포인트_사용_실패_잔액부족() {
        // given
        long userId = 1L;
        long initialPoint = 2_000L;
        long useAmount = 3_000L;

        log.info("[잔액 부족 테스트 시작] userId={}, initialPoint={}, useAmount={}", userId, initialPoint, useAmount);

        UserPoint current = new UserPoint(userId, initialPoint, System.currentTimeMillis());
        when(userPointTable.selectById(userId)).thenReturn(current);

        log.warn("[잔액 부족] userId={}, currentPoint={}, useAmount={}", userId, initialPoint, useAmount);

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pointService.usePoint(userId, useAmount);
        });

        assertEquals("잔액이 부족합니다. 현재 잔액: " + initialPoint, exception.getMessage());
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
    }

    @Test
    void 포인트_사용_실패_음수금액() {
        // given
        long userId = 1L;
        long useAmount = -1000L;

        log.info("[음수 금액 테스트 시작] userId={}, useAmount={}", userId, useAmount);

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pointService.usePoint(userId, useAmount);
        });

        log.warn("[음수 금액 입력] userId={}, useAmount={}", userId, useAmount);
        assertEquals("사용 금액은 0 이상이어야 합니다.", exception.getMessage());
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
    }
}
