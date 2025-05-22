package io.hhplus.tdd;

import io.hhplus.tdd.point.PointController;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PointController.class)
public class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PointService pointService;

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
    @DisplayName("유저는 존재하지만 포인트가 0인 경우 정상 응답")
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
}
