package com.vulntrend.batch.infrastructure.cvelist.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AffectedJson(String vendor, String product, String packageName, String defaultStatus,
        List<VersionJson> versions, List<String> cpes, @JsonProperty("packageURL") String packageUrl) {
}
