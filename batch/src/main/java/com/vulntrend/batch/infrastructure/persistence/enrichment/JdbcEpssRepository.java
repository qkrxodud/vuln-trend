package com.vulntrend.batch.infrastructure.persistence.enrichment;

import com.vulntrend.batch.domain.enrichment.EpssRepository;
import com.vulntrend.batch.domain.enrichment.EpssScore;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * EPSS 반영(WBS 2.2). vulnerability 에서는 epss_score·epss_percentile 두 칸만 바꾼다 — NVD 저장(백엔드 A)과 KEV 반영과 겹치지 않는다.
 * 파일 전체(약 40만 줄)가 아니라 취약점 목록에 있는 CVE 만 받아 바꾼다(골라내기는 수집기가 knownIds 로 한다).
 */
@Repository
@RequiredArgsConstructor
public class JdbcEpssRepository implements EpssRepository {

    private static final String KNOWN_IDS = "select vuln_id from vulnerability";
    private static final String COUNT = "select count(*) from vulnerability";
    private static final String APPLY = """
            update vulnerability
            set epss_score = :score, epss_percentile = :percentile
            where vuln_id = :id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Set<CveId> knownIds() {
        return jdbc.queryForList(KNOWN_IDS, new MapSqlParameterSource(), String.class).stream()
                .map(CveId::from)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional
    public int applyAll(List<EpssScore> scores) {
        SqlParameterSource[] rows = scores.stream()
                .map(JdbcEpssRepository::toRow)
                .toArray(SqlParameterSource[]::new);
        return Arrays.stream(jdbc.batchUpdate(APPLY, rows)).sum();
    }

    @Override
    public int vulnerabilityCount() {
        return Optional.ofNullable(jdbc.queryForObject(COUNT, new MapSqlParameterSource(), Integer.class)).orElse(0);
    }

    private static SqlParameterSource toRow(EpssScore score) {
        return new MapSqlParameterSource()
                .addValue("id", score.getCveId().getValue())
                .addValue("score", score.getScore())
                .addValue("percentile", score.getPercentile());
    }
}
