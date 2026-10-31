package com.vulntrend.batch.infrastructure.nvd.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NvdReference(String url, String source, List<String> tags) {

    public NvdReference {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
