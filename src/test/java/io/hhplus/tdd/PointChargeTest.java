package io.hhplus.tdd;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.*;

public class PointChargeTest {

    private PointService pointService;
    private UserPointTable userPointTable;

    private static final Logger log = LoggerFactory.getLogger(PointChargeTest.class);

    private static final long MAX_POINT = 100_000_000L; // 최대 포인트 1억

    @BeforeEach
    void setUp() {
        userPointTable = new UserPointTable();
        pointService = new PointService(userPointTable);
    }

    @Test
    void 포인트_정상_충전() {
        // given
        long userId = 1L;
        userPointTable.insertOrUpdate(userId, 1000L);
        log.info("충전 전 포인트: userId={}, point=1000", userId);

        // when
        UserPoint result = pointService.chargePoint(userId, 500L);

        // then
        log.info("충전 후 포인트: userId={}, point={}", result.id(), result.point());
        assertThat(result.point()).isEqualTo(1500L);
    }

    @Test
    void 포인트_충전_시_최대_포인트_초과_예외() {
        // given
        long userId = 2L;
        userPointTable.insertOrUpdate(userId, MAX_POINT - 1000L);
        log.info("충전 전 포인트: userId={}, point={}", userId, MAX_POINT - 1000L);


        // when & then
        assertThatThrownBy(() -> {
            log.info("충전 시도: userId={}, amount=2000", userId);
            pointService.chargePoint(userId, 2000L);
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 보유 포인트를 초과할 수 없습니다");

        log.info("예외 발생: 최대 포인트 초과 충전 시도");
    }

    @Test
    void 새_유저에게_포인트_충전() {
        // given
        long userId = 3L;

        // when
        UserPoint result = pointService.chargePoint(userId, 3000L);

        // log
        log.info("충전 결과: userId={}, 충전 후 포인트={}", result.id(), result.point());

        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(3000L);
    }
}
