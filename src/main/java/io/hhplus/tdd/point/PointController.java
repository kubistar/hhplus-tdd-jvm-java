package io.hhplus.tdd.point;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/point")
public class PointController {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }


    /**
     * TODO - 특정 유저의 포인트를 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}")
    public UserPoint point( @PathVariable long id) {

//        return new UserPoint(0, 0, 0);
        return pointService.getPoint(id);

    }

    /**
     * TODO - 특정 유저의 포인트 충전/이용 내역을 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}/histories")
    public List<PointHistory> history(
            @PathVariable long id
    ) {
        return List.of();
    }

    /**
     * TODO - 특정 유저의 포인트를 충전하는 기능을 작성해주세요.
     */
    /**
     * 특정 유저의 포인트를 충전하는 API
     *
     * @param id      충전할 유저의 ID
     * @param request 충전할 포인트가 담긴 요청 DTO
     * @return 충전 후 UserPoint 객체 반환
     */
    @PatchMapping("{id}/charge")
    public UserPoint charge(@PathVariable long id,
                            @RequestBody ChargeRequest request) {
        log.info("PATCH /point/{}/charge 요청됨. amount={}", id, request.amount());
        return pointService.chargePoint(id, request.amount());
    }

    /**
     * TODO - 특정 유저의 포인트를 사용하는 기능을 작성해주세요.
     */
    @PatchMapping("{id}/use")
    public UserPoint use(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        return new UserPoint(0, 0, 0);
    }
}
