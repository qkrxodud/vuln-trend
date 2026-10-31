package com.vulntrend.batch.infrastructure.nvd.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** source 가 nvd@nist.gov 면 NVD 가 낸 점수, 그 밖은 제조사(CNA) 점수 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NvdCvssMetric(String source, String type, NvdCvssData cvssData) {
}
