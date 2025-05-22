package io.hhplus.tdd.point;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 포인트 충전 요청 DTO
 */
@Data
@NoArgsConstructor(force = true)
public class ChargeRequest {

    private final long amount;

    @JsonCreator
    public ChargeRequest(@JsonProperty("amount") long amount) {
        this.amount = amount;
    }

    public long amount() {
        return amount;
    }
}