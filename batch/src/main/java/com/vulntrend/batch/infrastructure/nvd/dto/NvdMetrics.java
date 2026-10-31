package com.vulntrend.batch.infrastructure.nvd.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 3.1·3.0·4.0 점수를 읽는다(기획서 S2, 3.0 추가 확정 2026-10-31). 2.0 점수는 쓰지 않는다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NvdMetrics(List<NvdCvssMetric> cvssMetricV31, List<NvdCvssMetric> cvssMetricV30,
        List<NvdCvssMetric> cvssMetricV40) {

    public NvdMetrics {
        cvssMetricV31 = cvssMetricV31 == null ? List.of() : List.copyOf(cvssMetricV31);
        cvssMetricV30 = cvssMetricV30 == null ? List.of() : List.copyOf(cvssMetricV30);
        cvssMetricV40 = cvssMetricV40 == null ? List.of() : List.copyOf(cvssMetricV40);
    }
}
