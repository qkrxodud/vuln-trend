package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.AffectedProductJson;
import com.vulntrend.batch.application.export.dto.CveDetailJson;
import com.vulntrend.batch.application.export.dto.ReferenceLinkJson;
import com.vulntrend.batch.application.export.dto.SummaryJson;
import com.vulntrend.batch.domain.export.CveDetailRepository;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.export.WriteOutcome;
import com.vulntrend.batch.domain.export.dto.CveDetailListing;
import com.vulntrend.batch.domain.export.dto.ReferenceListing;
import com.vulntrend.batch.domain.export.dto.SummaryListing;
import com.vulntrend.batch.domain.product.ProductNames;
import com.vulntrend.batch.domain.stat.StatDays;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * cve/CVE-*.json (기획서 7장 P6·R5, WBS 4.2). 생성 범위 안의 CVE 마다 한 파일을 쓰고, 범위 밖 파일(철회되거나 순위에서 밀려난 CVE)은
 * 지운다 — 남겨 두면 옛 점수가 그대로 보인다.
 * - 목록 줄 필드 전부(TrendRows) + 원문 설명(없으면 빈 글)·발행/수정 시각(한국 시각)·KEV 등재일(KEV 일 때만)·요약·영향 제품·참고 링크
 * - 영향 제품의 product 는 사이트에 보일 이름(ProductNames), display_name·slug 는 표에 있을 때만(없으면 null — 계약 그대로).
 *   제조사는 원문 그대로
 * - 참고 링크는 벤더 어드바이저리 다음에 NVD 원문 링크를 붙이고, 그 뒤에 뉴스·PoC(저장소가 출처 순서로 준다)
 * - 상세는 500건씩 나눠 읽는다(in 목록 크기 제한)
 */
@Component
@RequiredArgsConstructor
public class CveDetailFileExport {

    private static final String FOLDER = "cve";
    private static final String NVD_DETAIL = "https://nvd.nist.gov/vuln/detail/";
    private static final String VENDOR = "vendor";
    private static final String NVD = "nvd";
    private static final int CHUNK = 500;

    private final CveDetailRepository repository;
    private final SiteDataWriter writer;

    public DetailFiles write(Collection<CveId> scope, Instant now, ProductNames names) {
        List<CveId> ids = List.copyOf(scope);
        Set<DataFilePath> kept = new HashSet<>();
        int written = 0;
        int unchanged = 0;
        for (int from = 0; from < ids.size(); from += CHUNK) {
            List<CveId> chunk = ids.subList(from, Math.min(from + CHUNK, ids.size()));
            for (CveDetailListing detail : repository.details(chunk, now)) {
                DataFilePath path = pathOf(detail.row().id());
                kept.add(path);
                WriteOutcome outcome = writer.write(path, json(detail, names));
                written += outcome == WriteOutcome.WRITTEN ? 1 : 0;
                unchanged += outcome == WriteOutcome.UNCHANGED ? 1 : 0;
            }
        }
        int removed = (int) writer.listFolder(FOLDER).stream()
                .filter(Predicate.not(kept::contains))
                .filter(writer::remove)
                .count();
        return DetailFiles.of(written, unchanged, removed);
    }

    private static DataFilePath pathOf(CveId id) {
        return DataFilePath.of(FOLDER + "/" + id.getValue() + ".json");
    }

    private static CveDetailJson json(CveDetailListing detail, ProductNames names) {
        return new CveDetailJson(TrendRows.from(detail.row(), names),
                Optional.ofNullable(detail.description()).orElse(""),
                koreanTime(detail.row().publishedAt()), koreanTime(detail.lastModifiedAt()),
                detail.row().kev() ? detail.kevAddedAt() : null,
                Optional.ofNullable(detail.summary()).map(CveDetailFileExport::summary).orElse(null),
                detail.affected().stream().map(a -> new AffectedProductJson(a.vendor(), names.shown(a.product()), a.displayName(),
                        a.slug(), a.ecosystem(), a.vulnerableRange(), a.fixedVersion())).toList(),
                references(detail));
    }

    private static SummaryJson summary(SummaryListing summary) {
        return new SummaryJson(summary.what(), summary.who(), summary.action(), summary.model(),
                koreanTime(summary.generatedAt()));
    }

    private static List<ReferenceLinkJson> references(CveDetailListing detail) {
        List<ReferenceListing> stored = detail.references();
        List<ReferenceLinkJson> links = new ArrayList<>();
        stored.stream().filter(link -> VENDOR.equals(link.source())).map(CveDetailFileExport::link).forEach(links::add);
        if (stored.stream().noneMatch(link -> NVD.equals(link.source()))) {
            links.add(new ReferenceLinkJson(NVD_DETAIL + detail.row().id().getValue(), NVD, null));
        }
        stored.stream().filter(Predicate.not(link -> VENDOR.equals(link.source()))).map(CveDetailFileExport::link)
                .forEach(links::add);
        return List.copyOf(links);
    }

    private static ReferenceLinkJson link(ReferenceListing link) {
        return new ReferenceLinkJson(link.url(), link.source(), link.title());
    }

    private static OffsetDateTime koreanTime(Instant instant) {
        return Objects.isNull(instant) ? null : instant.atZone(StatDays.CALENDAR).toOffsetDateTime();
    }
}
