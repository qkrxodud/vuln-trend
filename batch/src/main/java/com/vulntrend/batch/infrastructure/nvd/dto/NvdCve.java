package com.vulntrend.batch.infrastructure.nvd.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** NVD cve 객체 중 1.2 가 읽는 필드. 시각은 시간대 표시 없는 UTC 문자열로 온다(실응답 확인, 2026-10-30) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NvdCve(String id, String vulnStatus, String published, String lastModified,
        List<NvdDescription> descriptions, NvdMetrics metrics, List<NvdConfiguration> configurations,
        List<NvdReference> references) {

    public NvdCve {
        descriptions = descriptions == null ? List.of() : List.copyOf(descriptions);
        metrics = metrics == null ? new NvdMetrics(List.of(), List.of(), List.of()) : metrics;
        configurations = configurations == null ? List.of() : List.copyOf(configurations);
        references = references == null ? List.of() : List.copyOf(references);
    }
}
