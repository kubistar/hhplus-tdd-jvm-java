package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

@Service
public class PointService {

    private final UserPointTable userPointTable;

    public PointService(UserPointTable userPointTable) {
        this.userPointTable = userPointTable;
    }

    /**
     * 특정 유저의 포인트를 조회하는 메서드
     *
     * @param userId 조회할 유저 아이디
     * @return 해당 유저의 UserPoint 객체 (없으면 잔고 0인 빈 객체 반환)
     */
    public UserPoint getPoint(long userId) {
        return userPointTable.selectById(userId);
    }
}
