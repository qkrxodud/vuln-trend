package com.vulntrend.batch.infrastructure.cvelist.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CnaContainerJson(List<AffectedJson> affected) {
}
