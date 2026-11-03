package com.vulntrend.batch.infrastructure.cvelist.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** CVE JSON 5 레코드 중 쓰는 부분만(containers.cna.affected) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CveRecordJson(ContainersJson containers) {
}
