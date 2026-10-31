package com.vulntrend.batch.infrastructure.nvd.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NvdCpeMatch(boolean vulnerable, String criteria, String versionStartIncluding,
        String versionStartExcluding, String versionEndIncluding, String versionEndExcluding) {
}
