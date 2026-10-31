package com.vulntrend.batch.infrastructure.nvd.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** NVD CVE API 2.0 응답 한 쪽. 쓰지 않는 필드(format, version, timestamp)는 무시한다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NvdPage(int resultsPerPage, int startIndex, int totalResults, List<NvdVulnerability> vulnerabilities) {

    public NvdPage {
        vulnerabilities = vulnerabilities == null ? List.of() : List.copyOf(vulnerabilities);
    }
}
