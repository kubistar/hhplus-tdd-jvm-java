package io.hhplus.tdd;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PointServiceTest {

    private UserPointTable userPointTable;
    private PointService pointService;

    private static final Logger log = LoggerFactory.getLogger(PointServiceTest.class);

    @BeforeEach
    void setUp() {
        userPointTable = new UserPointTable();
        pointService = new PointService(userPointTable);
    }

    @Test
    void 유저가_없을때_빈_UserPoint_반환() {
        // given
        long userId = 1L;

        // when
        UserPoint result = pointService.getPoint(userId);

        // 로그 출력
        log.info("UserPoint 조회 결과 (없는 유저): {}", result);


        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(0);
    }

    @Test
    void 유저가_존재할때_UserPoint_정상조회() {
        // given
        long userId = 2L;
        userPointTable.insertOrUpdate(userId, 5000L);

        // when
        UserPoint result = pointService.getPoint(userId);

        // 로그 출력
        log.info("UserPoint 조회 결과 (존재하는 유저): {}", result);

        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(5000L);
    }


    @Test
    void 유저가_존재하지만_포인트가_0인_경우_정상조회() {
        // given
        long userId = 3L;
        userPointTable.insertOrUpdate(userId, 0L);
        log.info("포인트 0으로 유저 생성: userId={}, point={}", userId, 0L);

        // when
        UserPoint result = pointService.getPoint(userId);
        log.info("조회된 UserPoint: id={}, point={}, updateMillis={}",
                result.id(), result.point(), result.updateMillis());

        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(0L);
    }
}
