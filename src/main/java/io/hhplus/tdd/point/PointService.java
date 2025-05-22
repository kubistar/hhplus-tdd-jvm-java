package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    // 사용자 단위로 락을 걸기 위한 맵
    private final Map<Long, Object> userLocks = new ConcurrentHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(PointService.class);

    private static final long MAX_POINT = 100_000_000L; // 최대 포인트 제한

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {

        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
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
            log.warn("음수 금액 충전 시도됨: userId={}, amount={}", userId, amount);
            throw new IllegalArgumentException("충전 금액은 0 이상이어야 합니다.");
        }

        // 사용자별 lock 객체 가져오기
        Object lock = userLocks.computeIfAbsent(userId, k -> new Object());

        synchronized (lock) {
            // 현재 포인트 조회 (없으면 0으로 시작)
            UserPoint current = getPoint(userId);
            long newAmount = current.point() + amount;

            if (newAmount > MAX_POINT) {
                throw new IllegalArgumentException("최대 보유 포인트를 초과할 수 없습니다.");
            }

            // 저장
            UserPoint updated = userPointTable.insertOrUpdate(userId, newAmount);
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
            log.info("충전 완료 - userId={}, amount={}, 최종 point={}", userId, amount, updated.point());

            return updated;
        }
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

    public List<PointHistory> getHistories(long userId) {
        List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);
        if (histories.isEmpty()) {
            log.warn("userId={}에 대한 포인트 내역이 존재하지 않음", userId);
            throw new IllegalArgumentException("포인트 사용/충전 내역이 없습니다.");
        }
        log.info("userId={}의 포인트 내역 {}건 반환", userId, histories.size());
        return histories;
    }

}
