package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.ScoreBreakdownJson;
import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.enrichment.EpssShown;
import com.vulntrend.batch.domain.product.ProductNames;
import com.vulntrend.batch.domain.remedy.FixVersions;
import com.vulntrend.batch.domain.remedy.PatchLinks;
import com.vulntrend.batch.domain.score.WhyNowFacts;
import com.vulntrend.batch.domain.score.WhyNowTemplate;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import com.vulntrend.batch.domain.stat.StatDays;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 트렌드 목록 한 줄을 JSON 계약 모양(web/src/types/trend.ts TrendRow)으로 바꾼다(WBS 4.2).
 * - score 는 화면 점수(소수 1자리 항의 합을 반올림한 정수), score_breakdown 은 소수 1자리(R6)
 * - why_now 는 규칙 템플릿(3.2). DB 에 저장하지 않고 여기서 만든다
 * - 대표 제품은 사이트에 보일 이름(ProductNames — 표의 이름, 없으면 사람이 읽는 CPE 이름). 저장 원문은 그대로 둔다(B2)
 * - 한 줄 조치: fix_versions 는 대표 제품 범위의 미만 상한(FixVersions), patch_url 은 NVD 태그로 고른 첫 링크(PatchLinks, 없으면 null)
 * - product_family·attack_types 는 분류 키(사용자 결정 2026-11-01). 분류 전이면 null·빈 목록. 이름은 categories.json 에만
 * - epss·epss_percentile 은 화면이 보이는 자리까지만(EpssShown, S4). 점수 구성의 EPSS 항과 why_now 는 원래 값으로 센다
 * - published_at 은 공개 시각(한국 시각), 상세와 같은 값(사용자 결정 2026-11-01 공개일 필터·최신순)
 * - KEV 가 아니면 kev_ransomware 는 false, kev_due_date·kev_added_at 은 null(계약). kev_added_at 은 v6 화면용(사용자 승인 2026-11-01) 제목이 아직 없으면 CVE 번호
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TrendRows {

    public static TrendRowJson from(TrendListing listing, ProductNames names) {
        String id = listing.id().getValue();
        boolean ransomware = listing.kev() && listing.kevRansomware();
        WhyNowFacts facts = WhyNowFacts.of(listing.kev(), ransomware, listing.pocCount(), listing.epssPercentile(),
                listing.mentionCount(), listing.cvss());
        return new TrendRowJson(id, Optional.ofNullable(listing.title()).orElse(id),
                ScoreBreakdownJson.scoreOf(listing.breakdown()), ScoreBreakdownJson.from(listing.breakdown()),
                WhyNowTemplate.phraseOf(facts), listing.kev(), ransomware,
                listing.kev() ? listing.kevDueDate() : null,
                listing.kev() ? listing.kevAddedAt() : null, koreanTime(listing.publishedAt()), listing.pocCount(),
                Optional.ofNullable(listing.epss()).map(EpssShown::probability).orElse(null),
                Optional.ofNullable(listing.epssPercentile()).map(EpssShown::percentile).orElse(null), listing.cvss(), listing.cvssVersion(), listing.severity().name(),
                Optional.ofNullable(listing.primaryProduct()).map(names::shown).orElse(null), listing.productFamily(),
                List.copyOf(listing.attackTypes()),
                listing.summaryWhat(),
                FixVersions.fromAll(listing.remedy().primaryRanges()),
                PatchLinks.first(listing.remedy().nvdReferences()).orElse(null));
    }

    /** 저장 시각을 한국 시각(+09:00)으로. 없으면 null */
    static OffsetDateTime koreanTime(Instant instant) {
        return Objects.isNull(instant) ? null : instant.atZone(StatDays.CALENDAR).toOffsetDateTime();
    }
}
