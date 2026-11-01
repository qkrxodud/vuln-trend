package com.vulntrend.batch.application.export.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** meta.json (web/src/types/meta.ts Meta). 숫자가 붙은 이름은 이름 규칙(snake_case)이 숫자 앞에 밑줄을 넣지 않아 직접 적는다 */
public record MetaJson(OffsetDateTime updatedAt, String version, KpiCountsJson today, KpiCountsJson yesterday,
        @JsonProperty("kev_added_7d") int kevAdded7d, @JsonProperty("kev_added_prev_7d") int kevAddedPrev7d) {
}
