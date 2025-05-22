package io.hhplus.tdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.ChargeRequest;
import io.hhplus.tdd.point.PointController;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PointController.class)
public class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PointService pointService;

    @Autowired
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final long MAX_POINT = 100_000_000L;


    @Test
    @DisplayName("유저의 포인트를 정상적으로 조회한다")
    void getUserPoint() throws Exception {
        // given
        long userId = 1L;
        UserPoint mockUserPoint = new UserPoint(userId, 1000L, System.currentTimeMillis());

        when(pointService.getPoint(userId)).thenReturn(mockUserPoint);

        // when & then
        mockMvc.perform(get("/point/{id}", userId))
                .andDo(print()) // ★ 로그 출력 추가
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(1000));
    }

    @Test
    @DisplayName("유저가 없을 때 포인트 0으로 반환")
    void getUserPoint_whenUserDoesNotExist() throws Exception {
        long userId = 2L;
        when(pointService.getPoint(userId)).thenReturn(new UserPoint(userId, 0L, System.currentTimeMillis()));

        mockMvc.perform(get("/point/{id}", userId))
                .andDo(print()) // 로그 출력
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(0));
    }

    @Test
    @DisplayName("포인트가 0인 경우 정상 응답")
    void getUserPoint_whenUserExistsWithZeroPoint() throws Exception {
        long userId = 3L;
        UserPoint userPoint = new UserPoint(userId, 0L, System.currentTimeMillis());

        when(pointService.getPoint(userId)).thenReturn(userPoint);

        mockMvc.perform(get("/point/{id}", userId))
                .andDo(print()) // 로그 출력
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(0));
    }


    @Test
    @DisplayName("포인트_정상_충전")
    void 포인트_정상_충전() throws Exception {
        long userId = 1L;
        long amount = 5000L;
        UserPoint result = new UserPoint(userId, amount, System.currentTimeMillis());

        when(pointService.chargePoint(userId, amount)).thenReturn(result);

        mockMvc.perform(
                        patch("/point/{id}/charge", userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ChargeRequest(amount)))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) userId)))
                .andExpect(jsonPath("$.point", is((int) amount)))
                .andExpect(jsonPath("$.updateMillis").isNumber());
    }


    @Test
    void 포인트_충전_시_최대_포인트_초과_예외() throws Exception {
        // given
        long currentPoint = MAX_POINT - 1000L;
        long chargeAmount = 2000L;
        long userId = 2L;

        // PointService의 getPoint()가 현재 포인트를 반환하도록 설정
        when(pointService.chargePoint(userId, chargeAmount))
                .thenThrow(new IllegalArgumentException("최대 보유 포인트를 초과할 수 없습니다."));

        ChargeRequest request = new ChargeRequest(chargeAmount);
        String jsonRequest = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest()) // 예외가 발생하면 400 Bad Request로 반환하는 게 일반적
                .andExpect(content().string(org.hamcrest.Matchers.containsString("최대 보유 포인트를 초과할 수 없습니다.")));
    }

    @Test
    @DisplayName("새_유저_포인트_충전")
    void 새_유저_포인트_충전() throws Exception {
        long newUserId = 99L;
        long amount = 1000L;
        UserPoint result = new UserPoint(newUserId, amount, System.currentTimeMillis());

        when(pointService.chargePoint(newUserId, amount)).thenReturn(result);

        mockMvc.perform(
                        patch("/point/{id}/charge", newUserId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ChargeRequest(amount)))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) newUserId)))
                .andExpect(jsonPath("$.point", is((int) amount)))
                .andExpect(jsonPath("$.updateMillis").isNumber());
    }
}
