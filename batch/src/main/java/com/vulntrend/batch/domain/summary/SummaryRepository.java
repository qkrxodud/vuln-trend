package com.vulntrend.batch.domain.summary;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.util.List;

/** 요약 저장소. 대상 고르기·호출 기록·요약 저장을 맡는다(WBS 6.3) */
public interface SummaryRepository {

    /** 트렌드 점수 상위 top 건 중 요약이 없고, 버리거나 거절된 적도 없는 CVE */
    List<SummarySource> findTopWithoutSummary(int top);

    /** since 이후 Claude 를 부른 횟수(하루 상한 세기) */
    int countCallsSince(Instant since);

    void recordCall(CveId cveId, SummaryOutcome outcome, String model, Instant calledAt);

    /**
     * 요약을 처음 한 번만 남긴다(이미 있으면 그대로). 제목이 아직 NVD 설명에서 만든 임시 제목이면 요약 제목으로 바꾼다.
     * GHSA 처럼 더 앞선 출처가 고친 제목은 덮지 않는다(기획서 3장 title 규칙: GHSA > 요약 what > 설명 첫 문장).
     */
    void saveSummary(CveId cveId, KoreanSummary summary, String model, Instant generatedAt,
            String descriptionTitle, String summaryTitle);
}
