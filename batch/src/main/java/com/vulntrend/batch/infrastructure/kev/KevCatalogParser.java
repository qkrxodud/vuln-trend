package com.vulntrend.batch.infrastructure.kev;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.enrichment.KevEntry;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.global.error.exception.DomainValidationException;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import com.vulntrend.batch.infrastructure.kev.dto.KevVulnerability;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CISA KEV JSON 을 실제 악용 목록 건으로 바꾼다. 모양이 틀린 건(CVE 번호·날짜)은 건너뛰고 수만 남긴다.
 * 목록 칸이 없거나 쓸 수 있는 건이 하나도 없으면 실패다 — 빈 목록을 반영하면 모든 실제 악용 표시가 지워지기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KevCatalogParser {

    private static final String SOURCE = SourceName.KEV.getKey();

    private final ObjectMapper objectMapper;

    public List<KevEntry> parse(String body) {
        JsonNode list = vulnerabilities(body);
        List<KevEntry> entries = new ArrayList<>();
        int skipped = 0;
        for (JsonNode node : list) {
            Optional<KevEntry> entry = toEntry(node);
            entry.ifPresent(entries::add);
            skipped += entry.isPresent() ? 0 : 1;
        }
        if (skipped > 0) {
            log.warn("KEV catalog had {} entries with an unusable CVE id or date; they were skipped.", skipped);
        }
        if (entries.isEmpty()) {
            throw new SourceFetchException(SOURCE, "the catalog had no usable entries.");
        }
        return entries;
    }

    private JsonNode vulnerabilities(String body) {
        JsonNode root = readTree(body);
        JsonNode list = root.path("vulnerabilities");
        if (list.isArray()) {
            return list;
        }
        throw new SourceFetchException(SOURCE, "the answer has no vulnerabilities list.");
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException failure) {
            throw new SourceFetchException(SOURCE, "unreadable answer (" + failure.getClass().getSimpleName() + ").");
        }
    }

    private Optional<KevEntry> toEntry(JsonNode node) {
        try {
            KevVulnerability item = objectMapper.treeToValue(node, KevVulnerability.class);
            return Optional.of(KevEntry.of(CveId.from(item.cveId()), date(item.dateAdded()), date(item.dueDate()),
                    item.knownRansomwareCampaignUse(), objectMapper.writeValueAsString(node)));
        } catch (JsonProcessingException | DomainValidationException | DateTimeParseException unusable) {
            return Optional.empty();
        }
    }

    private static LocalDate date(String raw) {
        return Objects.isNull(raw) || raw.isBlank() ? null : LocalDate.parse(raw.trim());
    }
}
