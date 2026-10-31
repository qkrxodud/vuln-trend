package com.vulntrend.batch.infrastructure.persistence.score;

import com.vulntrend.batch.domain.score.ScoreBreakdown;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 트렌드 점수식 조각(기획서 6장 MVP 점수식, WBS 3.1). 점수 = (KEV 50 + 랜섬웨어 10 + EPSS×30 + CVSS) × 0.5^(경과일/30).
 * - 경과일 기준은 greatest(published_at, kev_added_at, last_mention_at)(P1). greatest 는 비어 있는 값을 건너뛴다 —
 *   기획서 SQL 의 coalesce 와 같은 뜻이다. 셋 다 없으면 감쇠하지 않는다(비율 1).
 * - kev_added_at 은 date 라서 세계 표준시 자정으로 바꿔 센다. ::timestamptz 로 바꾸면 저장소 세션 시간대(KST 등)를 타서 9시간 어긋난다.
 * - 기준 시각이 이번 실행보다 늦으면 경과일을 0 으로 둔다(점수가 감쇠 전보다 커지지 않는다).
 * - 랜섬웨어 10점은 KEV 에 올라 있을 때만 센다. :now 는 SQL now() 가 아니라 이번 실행 시각(Clock)이다.
 * 점수 갱신·항별 기여도·목록 조회가 모두 이 조각을 써서 서로 어긋나지 않는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TrendScoreSql {

    static final String KEV_TERM = "(case when coalesce(is_kev, false) then 50 else 0 end)";
    static final String RANSOMWARE_TERM =
            "(case when coalesce(is_kev, false) and coalesce(kev_ransomware, false) then 10 else 0 end)";
    static final String EPSS_TERM = "(coalesce(epss_score, 0) * 30)";
    static final String CVSS_TERM = "coalesce(cvss_score, 0)";
    static final String REFERENCE_TIME =
            "greatest(published_at, (kev_added_at::timestamp at time zone 'UTC'), last_mention_at)";
    static final String AGE_DAYS =
            "greatest(0, extract(epoch from (cast(:now as timestamptz) - " + REFERENCE_TIME + ")) / 86400)";
    static final String DECAY_FACTOR = "coalesce(power(0.5::numeric, " + AGE_DAYS + " / 30.0), 1)";
    static final String TREND_TOTAL = "(" + KEV_TERM + " + " + RANSOMWARE_TERM + " + " + EPSS_TERM + " + " + CVSS_TERM
            + ") * " + DECAY_FACTOR;
    /** 항별 기여도를 읽는 열들. breakdown(rs) 와 짝이다 */
    static final String BREAKDOWN_COLUMNS = KEV_TERM + " as kev, " + RANSOMWARE_TERM + " as ransomware, "
            + EPSS_TERM + " as epss, " + CVSS_TERM + " as cvss, " + DECAY_FACTOR + " as decay_factor";

    static ScoreBreakdown breakdown(ResultSet rs) throws SQLException {
        return ScoreBreakdown.of(rs.getBigDecimal("kev"), rs.getBigDecimal("ransomware"), rs.getBigDecimal("epss"),
                rs.getBigDecimal("cvss"), rs.getBigDecimal("decay_factor"));
    }
}
