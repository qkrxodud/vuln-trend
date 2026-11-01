package com.vulntrend.batch.application.export.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** categories.json 의 분류 하나. 숫자가 붙은 이름은 이름 규칙(snake_case)이 숫자 앞에 밑줄을 넣지 않아 직접 적는다 */
public record CategoryJson(String key, String label, @JsonProperty("count_7d") int count7d,
        @JsonProperty("count_30d") int count30d, int kevCount, List<TrendRowJson> top) {
}
