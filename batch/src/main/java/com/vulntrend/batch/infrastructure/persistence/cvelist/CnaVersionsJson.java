package com.vulntrend.batch.infrastructure.persistence.cvelist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.cvelist.CnaVersion;
import com.vulntrend.batch.infrastructure.cvelist.dto.VersionJson;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** cna_affected.versions(jsonb) ↔ CnaVersion 목록. 모양은 CVE JSON 5 versions[] 와 같은 이름(VersionJson) */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CnaVersionsJson {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<VersionJson>> LIST = new TypeReference<>() {
    };

    static String write(List<CnaVersion> versions) {
        List<VersionJson> rows = versions.stream()
                .map(version -> new VersionJson(version.getVersion(), version.getStatus(),
                        version.getLessThan().orElse(null), version.getLessThanOrEqual().orElse(null),
                        version.getVersionType().orElse(null)))
                .toList();
        try {
            return JSON.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("CNA versions could not be written as JSON.", e);
        }
    }

    static List<CnaVersion> read(String json) {
        try {
            return JSON.readValue(json, LIST).stream()
                    .map(row -> CnaVersion.of(row.version(), row.status(), row.lessThan(), row.lessThanOrEqual(),
                            row.versionType()))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored CNA versions could not be read.", e);
        }
    }
}
