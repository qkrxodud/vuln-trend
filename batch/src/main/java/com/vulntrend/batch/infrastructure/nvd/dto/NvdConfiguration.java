package com.vulntrend.batch.infrastructure.nvd.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NvdConfiguration(List<NvdNode> nodes) {

    public NvdConfiguration {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
