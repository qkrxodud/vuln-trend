package com.vulntrend.batch.infrastructure.nvd.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NvdNode(List<NvdCpeMatch> cpeMatch) {

    public NvdNode {
        cpeMatch = cpeMatch == null ? List.of() : List.copyOf(cpeMatch);
    }
}
