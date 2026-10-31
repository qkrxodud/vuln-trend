package com.vulntrend.batch.infrastructure.nvd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.AffectedProduct;
import com.vulntrend.batch.domain.vulnerability.CollectedCve;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.CvssMetric;
import com.vulntrend.batch.domain.vulnerability.CvssOrigin;
import com.vulntrend.batch.domain.vulnerability.CvssSelection;
import com.vulntrend.batch.domain.vulnerability.CvssVersion;
import com.vulntrend.batch.domain.vulnerability.ReferenceLink;
import com.vulntrend.batch.domain.vulnerability.Vulnerability;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import com.vulntrend.batch.infrastructure.nvd.dto.NvdCpeMatch;
import com.vulntrend.batch.infrastructure.nvd.dto.NvdCve;
import com.vulntrend.batch.infrastructure.nvd.dto.NvdCvssMetric;
import com.vulntrend.batch.infrastructure.nvd.dto.NvdDescription;
import com.vulntrend.batch.infrastructure.nvd.dto.NvdReference;
import com.vulntrend.batch.infrastructure.nvd.dto.NvdVulnerability;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * NVD cve 한 건을 도메인 값으로 읽는다(WBS 1.2, 기획서 3장 수집 규칙).
 * - vulnStatus=Rejected → 철회(취약점으로 만들지 않음)
 * - CVSS: 3.1·3.0·4.0 점수를 모두 넘기고 고르는 것은 CvssSelection
 * - 설명: 영어, 없으면 첫 설명 / 제목: Vulnerability 가 첫 문장 80자로
 * - CPE: vulnerable=true 만, 같은 제품은 한 줄로 합침 / 링크: Vendor Advisory·Patch 표시만, 주소가 겹치면 한 번
 * - 시각: 시간대 표시가 없으면 UTC 로 읽음
 */
@Component
@RequiredArgsConstructor
public class NvdCveMapper {

    private static final String REJECTED = "Rejected";
    private static final String ENGLISH = "en";
    private static final String NVD_SOURCE = "nvd@nist.gov";
    private static final Set<String> VENDOR_TAGS = Set.of("Vendor Advisory", "Patch");

    private final ObjectMapper objectMapper;

    public CollectedCve map(NvdVulnerability item, Instant seenAt) {
        NvdCve cve = read(item);
        CveId id = CveId.from(cve.id());
        String raw = item.cve().toString();
        if (REJECTED.equalsIgnoreCase(cve.vulnStatus())) {
            return CollectedCve.rejected(id, raw);
        }
        Vulnerability vulnerability = Vulnerability.fromNvd(id, description(cve), cvss(cve),
                time(cve.published()), time(cve.lastModified()), affectedProducts(id, cve), references(id, cve, seenAt));
        return CollectedCve.accepted(vulnerability, raw);
    }

    private NvdCve read(NvdVulnerability item) {
        try {
            return objectMapper.treeToValue(item.cve(), NvdCve.class);
        } catch (JsonProcessingException failure) {
            throw new SourceFetchException(SourceName.NVD.getKey(),
                    "unreadable CVE record (" + failure.getClass().getSimpleName() + ").");
        }
    }

    private static String description(NvdCve cve) {
        return cve.descriptions().stream()
                .filter(d -> ENGLISH.equals(d.lang()))
                .findFirst()
                .or(() -> cve.descriptions().stream().findFirst())
                .map(NvdDescription::value)
                .orElse(null);
    }

    private static CvssSelection cvss(NvdCve cve) {
        Stream<CvssMetric> v31 = cve.metrics().cvssMetricV31().stream().map(m -> metric(CvssVersion.V3_1, m));
        Stream<CvssMetric> v30 = cve.metrics().cvssMetricV30().stream().map(m -> metric(CvssVersion.V3_0, m));
        Stream<CvssMetric> v40 = cve.metrics().cvssMetricV40().stream().map(m -> metric(CvssVersion.V4_0, m));
        return CvssSelection.choose(Stream.of(v31, v30, v40).flatMap(metrics -> metrics).toList());
    }

    private static CvssMetric metric(CvssVersion version, NvdCvssMetric metric) {
        CvssOrigin origin = NVD_SOURCE.equals(metric.source()) ? CvssOrigin.NVD : CvssOrigin.CNA;
        return CvssMetric.of(version, origin, metric.cvssData().baseScore());
    }

    // NVD 는 "2021-12-10T10:15:09.143" 처럼 시간대 없이 UTC 를 준다. 시간대가 붙어 오면 그것을 따른다
    private static Instant time(String text) {
        if (Objects.isNull(text)) {
            return null;
        }
        TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parse(text);
        if (parsed.isSupported(ChronoField.OFFSET_SECONDS)) {
            return OffsetDateTime.from(parsed).toInstant();
        }
        return LocalDateTime.from(parsed).toInstant(ZoneOffset.UTC);
    }

    private static List<AffectedProduct> affectedProducts(CveId id, NvdCve cve) {
        List<AffectedProduct> products = new ArrayList<>();
        cve.configurations().stream()
                .flatMap(configuration -> configuration.nodes().stream())
                .flatMap(node -> node.cpeMatch().stream())
                .filter(NvdCpeMatch::vulnerable)
                .forEach(match -> CpeName.parse(match.criteria())
                        .ifPresent(name -> products.add(
                                AffectedProduct.of(id, name.getVendor(), name.getProduct(), range(match, name)))));
        return AffectedProduct.mergeSameProduct(products);
    }

    // ">=2.0.1, <2.3.1" 꼴. 범위 표시가 없고 버전이 정해져 있으면 "=2.0-beta9", 그것도 없으면 모든 버전(비움)
    private static String range(NvdCpeMatch match, CpeName name) {
        List<String> bounds = new ArrayList<>();
        Optional.ofNullable(match.versionStartIncluding()).ifPresent(v -> bounds.add(">=" + v));
        Optional.ofNullable(match.versionStartExcluding()).ifPresent(v -> bounds.add(">" + v));
        Optional.ofNullable(match.versionEndIncluding()).ifPresent(v -> bounds.add("<=" + v));
        Optional.ofNullable(match.versionEndExcluding()).ifPresent(v -> bounds.add("<" + v));
        if (bounds.isEmpty()) {
            return name.getVersion().map(v -> "=" + v + name.getUpdate().map(u -> "-" + u).orElse("")).orElse(null);
        }
        return String.join(", ", bounds);
    }

    private static List<ReferenceLink> references(CveId id, NvdCve cve, Instant seenAt) {
        return cve.references().stream()
                .filter(NvdCveMapper::isVendorReference)
                .map(NvdReference::url)
                .filter(Objects::nonNull)
                .distinct()
                .map(url -> ReferenceLink.vendor(id, url, seenAt))
                .toList();
    }

    private static boolean isVendorReference(NvdReference reference) {
        return reference.tags().stream().anyMatch(VENDOR_TAGS::contains);
    }
}
