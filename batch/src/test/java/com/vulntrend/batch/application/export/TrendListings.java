package com.vulntrend.batch.application.export;

import com.vulntrend.batch.domain.export.dto.AffectedListing;
import com.vulntrend.batch.domain.export.dto.CveDetailListing;
import com.vulntrend.batch.domain.export.dto.ReferenceListing;
import com.vulntrend.batch.domain.export.dto.SummaryListing;
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

    static TrendListing kev(String id) {
        return new TrendListing(CveId.from(id), "Chrome V8 타입 혼동", TWELVE_DAYS_OLD_KEV, true, false,
                LocalDate.parse("2026-11-03"), 0, 0, new BigDecimal("0.94310"), new BigDecimal("0.99900"),
                new BigDecimal("9.8"), "3.1", Severity.CRITICAL, "Chrome", "악성 웹페이지로 원격 코드 실행",
                Instant.parse("2026-10-19T00:00:00Z"));
    }

    static TrendListing kevRansomware(String id) {
        TrendListing kev = kev(id);
        return new TrendListing(kev.id(), kev.title(), kev.breakdown(), true, true, kev.kevDueDate(), 0, 0, kev.epss(),
                kev.epssPercentile(), kev.cvss(), kev.cvssVersion(), kev.severity(), kev.primaryProduct(),
                kev.summaryWhat(), kev.publishedAt());
    }

    /** kev 와 같고 대표 제품만 다른 줄(저장 원문 그대로) */
    static TrendListing kevWithProduct(String id, String product) {
        TrendListing kev = kev(id);
        return new TrendListing(kev.id(), kev.title(), kev.breakdown(), true, false, kev.kevDueDate(), 0, 0, kev.epss(),
                kev.epssPercentile(), kev.cvss(), kev.cvssVersion(), kev.severity(), product, kev.summaryWhat(),
                kev.publishedAt());
    }

    static TrendListing unrated(String id) {
        return new TrendListing(CveId.from(id), null, ScoreBreakdown.of(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE), false, true, LocalDate.parse("2026-11-03"), 0, 0,
                null, null, null, null, Severity.UNRATED, null, null, Instant.parse("2026-10-30T00:00:00Z"));
    }

    static CveDetailListing kevDetail(String id) {
        return new CveDetailListing(kev(id), "Type confusion in V8 in Google Chrome.",
                Instant.parse("2026-10-20T03:00:00Z"), LocalDate.parse("2026-10-19"),
                new SummaryListing("원격 코드 실행", "Chrome 사용자", "최신 버전으로 업데이트", "claude-haiku-5-5",
                        Instant.parse("2026-10-30T00:00:00Z")),
                List.of(new AffectedListing("google", "chrome", "Chrome", "chrome", null, "< 130.0.6723.58", null)),
                List.of(new ReferenceListing("https://chromereleases.googleblog.com/x", "vendor", null)));
    }

    static CveDetailListing bareDetail(String id) {
        return new CveDetailListing(unrated(id), null, null, LocalDate.parse("2026-10-19"), null, List.of(), List.of());
    }
}
