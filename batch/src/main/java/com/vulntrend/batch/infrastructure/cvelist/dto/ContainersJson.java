package com.vulntrend.batch.infrastructure.cvelist.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContainersJson(CnaContainerJson cna) {
}
