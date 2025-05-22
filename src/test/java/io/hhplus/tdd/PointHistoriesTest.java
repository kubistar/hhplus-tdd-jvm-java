package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


public class PointHistoriesTest {

    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;
    private PointService pointService;

    @BeforeEach
    void setUp() {
        userPointTable = mock(UserPointTable.class);
        pointHistoryTable = mock(PointHistoryTable.class);
        pointService = new PointService(userPointTable, pointHistoryTable);
    }

    @Test
    void 포인트_충전시_히스토리에_CHARGE_기록이_추가되어야_한다() {
        // given
        long userId = 1L;
        long amount = 5000L;
        long currentMillis = System.currentTimeMillis();

        when(userPointTable.selectById(userId)).thenReturn(new UserPoint(userId, 0, currentMillis));
        when(userPointTable.insertOrUpdate(userId, 5000L)).thenReturn(new UserPoint(userId, 5000L, System.currentTimeMillis()));

        // when
        UserPoint updated = pointService.chargePoint(userId, amount);

        // then
        assertEquals(5000L, updated.point());

        // 히스토리 기록 여부 확인
        verify(pointHistoryTable, times(1)).insert(eq(userId), eq(amount), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    void 포인트_내역이_없는_경우_예외를_던진다() {
        // given
        long userId = 2L;
        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(List.of());

        // when & then
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> pointService.getHistories(userId));
        assertEquals("포인트 사용/충전 내역이 없습니다.", e.getMessage());
    }

    @Test
    void 포인트_내역이_있으면_정상_리턴한다() {
        // given
        long userId = 3L;
        List<PointHistory> mockHistories = List.of(
                new PointHistory(1L, userId, 1000L, TransactionType.CHARGE, System.currentTimeMillis())
        );

        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(mockHistories);

        // when
        List<PointHistory> result = pointService.getHistories(userId);

        // then
        assertEquals(1, result.size());
        assertEquals(TransactionType.CHARGE, result.get(0).type());
        verify(pointHistoryTable, times(1)).selectAllByUserId(userId);
    }
}
