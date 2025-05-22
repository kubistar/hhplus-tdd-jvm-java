package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PointService {

    private final UserPointTable userPointTable;

    private static final Logger log = LoggerFactory.getLogger(PointService.class);

    private static final long MAX_POINT = 100_000_000L; // 최대 포인트 제한

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


    /**
     * 특정 유저의 포인트를 충전하는 메서드
     *
     * @param userId 포인트를 충전할 유저의 ID
     * @param amount 충전할 포인트 양 (0 이상의 정수)
     * @return 충전된 후의 UserPoint 객체
     */
    public UserPoint chargePoint(long userId, long amount) {
        log.info("Charging userId={} with amount={}", userId, amount);

        if (amount < 0) {
            throw new IllegalArgumentException("충전 금액은 0 이상이어야 합니다.");
        }

        // 현재 포인트 조회 (없으면 0으로 시작)
        UserPoint current = getPoint(userId);
        long newAmount = current.point() + amount;

        if (newAmount > MAX_POINT) {
            throw new IllegalArgumentException("최대 보유 포인트를 초과할 수 없습니다.");
        }

        // 테이블에 저장
        UserPoint updated = userPointTable.insertOrUpdate(userId, newAmount);
        log.info("Charged. New point for userId={} is {}", userId, updated.point());

        return updated;
    }

    /**
     * 특정 유저의 포인트를 사용하는 메서드
     *
     * @param userId 포인트를 사용할 유저의 ID
     * @param amount 사용할 포인트 양 (0 이상의 정수)
     * @return 사용 후의 UserPoint 객체
     */
    public UserPoint usePoint(long userId, long amount) {
        log.info("Using points. userId={} amount={}", userId, amount);

        if (amount < 0) {
            throw new IllegalArgumentException("사용 금액은 0 이상이어야 합니다.");
        }

        // 현재 포인트 조회
        UserPoint current = getPoint(userId);

        if (current.point() < amount) {
            throw new IllegalArgumentException("잔액이 부족합니다. 현재 잔액: " + current.point());
        }

        long newAmount = current.point() - amount;
        UserPoint updated = userPointTable.insertOrUpdate(userId, newAmount);

        log.info("사용한 포인트. 잔액 userId={} is {}", userId, updated.point());

        return updated;
    }

}
