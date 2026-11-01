package com.vulntrend.batch.application.export;

import com.vulntrend.batch.domain.export.dto.AffectedListing;
import com.vulntrend.batch.domain.export.dto.CveDetailListing;
import com.vulntrend.batch.domain.export.dto.ReferenceListing;
import com.vulntrend.batch.domain.export.dto.SummaryListing;
import com.vulntrend.batch.domain.remedy.dto.RemedyListing;
import com.vulntrend.batch.domain.remedy.dto.TaggedLink;
import com.vulntrend.batch.domain.score.ScoreBreakdown;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 시험용 목록 줄 */
final class TrendListings {

    /** 12일 지난 KEV CVE, EPSS 0.94310, CVSS 9.8 — 화면 점수 67 */
    static final ScoreBreakdown TWELVE_DAYS_OLD_KEV = ScoreBreakdown.of(new BigDecimal("50"), BigDecimal.ZERO,
            new BigDecimal("28.293"), new BigDecimal("9.8"), new BigDecimal("0.757858283255199"));

    private TrendListings() {
    }

    /** 크롬 패치 공지(NVD 태그: 출시 노트·제조사 공지)와 끊긴 이슈 링크 */
    static final List<TaggedLink> CHROME_NVD_LINKS = List.of(
            new TaggedLink("https://chromereleases.googleblog.com/x", List.of("Release Notes", "Vendor Advisory")),
            new TaggedLink("https://crbug.com/1", List.of("Issue Tracking", "Broken Link")));
    static final String APPLY_UPDATES = "Apply updates per vendor instructions.";

    static TrendListing kev(String id) {
        return new TrendListing(CveId.from(id), "Chrome V8 타입 혼동", TWELVE_DAYS_OLD_KEV, true, false,
                LocalDate.parse("2026-11-03"), LocalDate.parse("2026-10-19"), 0, 0, new BigDecimal("0.94310"),
                new BigDecimal("0.99900"), new BigDecimal("9.8"), "3.1", Severity.CRITICAL, "Chrome",
                "악성 웹페이지로 원격 코드 실행", Instant.parse("2026-10-19T00:00:00Z"),
                new RemedyListing(List.of("< 130.0.6723.58"), CHROME_NVD_LINKS, APPLY_UPDATES));
    }

    static TrendListing kevRansomware(String id) {
        TrendListing kev = kev(id);
        return new TrendListing(kev.id(), kev.title(), kev.breakdown(), true, true, kev.kevDueDate(),
                kev.kevAddedAt(), 0, 0, kev.epss(), kev.epssPercentile(), kev.cvss(), kev.cvssVersion(),
                kev.severity(), kev.primaryProduct(), kev.summaryWhat(), kev.publishedAt(), kev.remedy());
    }

    /** kev 와 같고 제목만 다른 줄 */
    static TrendListing kevTitled(String id, String title) {
        TrendListing kev = kev(id);
        return new TrendListing(kev.id(), title, kev.breakdown(), true, false, kev.kevDueDate(), kev.kevAddedAt(),
                0, 0, kev.epss(), kev.epssPercentile(), kev.cvss(), kev.cvssVersion(), kev.severity(),
                kev.primaryProduct(), kev.summaryWhat(), kev.publishedAt(), kev.remedy());
    }

    /** kev 와 같고 대표 제품만 다른 줄(저장 원문 그대로) */
    static TrendListing kevWithProduct(String id, String product) {
        return kevWith(id, product, kev(id).remedy());
    }

    /** kev 와 같고 대표 제품·조치 정보만 다른 줄 */
    static TrendListing kevWith(String id, String product, RemedyListing remedy) {
        TrendListing kev = kev(id);
        return new TrendListing(kev.id(), kev.title(), kev.breakdown(), true, false, kev.kevDueDate(),
                kev.kevAddedAt(), 0, 0, kev.epss(), kev.epssPercentile(), kev.cvss(), kev.cvssVersion(),
                kev.severity(), product, kev.summaryWhat(), kev.publishedAt(), remedy);
    }

    /** KEV 가 아닌 미평가 CVE. 저장소에 KEV 기한·등재일·요구 조치가 남아 있어도 싣지 않아야 한다 */
    static TrendListing unrated(String id) {
        return new TrendListing(CveId.from(id), null, ScoreBreakdown.of(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE), false, true, LocalDate.parse("2026-11-03"),
                LocalDate.parse("2026-10-19"), 0, 0, null, null, null, null, Severity.UNRATED, null, null,
                Instant.parse("2026-10-30T00:00:00Z"), noRemedy(APPLY_UPDATES));
    }

    static RemedyListing noRemedy(String kevRequiredAction) {
        return new RemedyListing(List.of(), List.of(), kevRequiredAction);
    }

    static CveDetailListing kevDetail(String id) {
        return new CveDetailListing(kev(id), "Type confusion in V8 in Google Chrome.",
                Instant.parse("2026-10-20T03:00:00Z"),
                new SummaryListing("원격 코드 실행", "Chrome 사용자", "최신 버전으로 업데이트", "claude-haiku-5-5",
                        Instant.parse("2026-10-30T00:00:00Z")),
                List.of(new AffectedListing("google", "chrome", "Chrome", "chrome", null, "< 130.0.6723.58", null)),
                List.of(new ReferenceListing("https://chromereleases.googleblog.com/x", "vendor", null)));
    }

    static CveDetailListing bareDetail(String id) {
        return new CveDetailListing(unrated(id), null, null, null, List.of(), List.of());
    }
}
