package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.ScoreBreakdownJson;
import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.score.WhyNowFacts;
import com.vulntrend.batch.domain.score.WhyNowTemplate;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 트렌드 목록 한 줄을 JSON 계약 모양(web/src/types/trend.ts TrendRow)으로 바꾼다(WBS 4.2).
 * - score 는 화면 점수(소수 1자리 항의 합을 반올림한 정수), score_breakdown 은 소수 1자리(R6)
 * - why_now 는 규칙 템플릿(3.2). DB 에 저장하지 않고 여기서 만든다
 * - KEV 가 아니면 kev_ransomware 는 false, kev_due_date 는 null(계약). 제목이 아직 없으면 CVE 번호
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TrendRows {

    public static TrendRowJson from(TrendListing listing) {
        String id = listing.id().getValue();
        boolean ransomware = listing.kev() && listing.kevRansomware();
        WhyNowFacts facts = WhyNowFacts.of(listing.kev(), ransomware, listing.pocCount(), listing.epssPercentile(),
                listing.mentionCount(), listing.cvss());
        return new TrendRowJson(id, Optional.ofNullable(listing.title()).orElse(id),
                ScoreBreakdownJson.scoreOf(listing.breakdown()), ScoreBreakdownJson.from(listing.breakdown()),
                WhyNowTemplate.phraseOf(facts), listing.kev(), ransomware,
                listing.kev() ? listing.kevDueDate() : null, listing.pocCount(), listing.epss(),
                listing.epssPercentile(), listing.cvss(), listing.cvssVersion(), listing.severity().name(),
                listing.primaryProduct(), listing.summaryWhat());
    }
}
