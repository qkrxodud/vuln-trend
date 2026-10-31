package com.vulntrend.batch.infrastructure.nvd.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NvdDescription(String lang, String value) {
}
