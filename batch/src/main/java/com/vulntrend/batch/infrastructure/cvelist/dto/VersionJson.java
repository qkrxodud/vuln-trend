package com.vulntrend.batch.infrastructure.cvelist.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** versions[] 한 줄. 저장소의 versions jsonb 도 같은 모양을 쓴다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VersionJson(String version, String status, String lessThan, String lessThanOrEqual,
        String versionType) {
}
