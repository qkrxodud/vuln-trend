package com.vulntrend.batch.infrastructure.persistence.cvelist;

import com.vulntrend.batch.domain.cvelist.CnaAffected;
import com.vulntrend.batch.domain.cvelist.CvelistFetchRepository;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * CVE.org 보강 대상 고르기와 저장(V6 cna_affected·cvelist_fetch).
 * 채운 CVE(cna_affected 가 있는 것)는 고르지 않는다. 못 채운 CVE 는 마지막 시도(attempted_at)가 retryBefore 보다 앞설 때만.
 */
@Repository
@RequiredArgsConstructor
public class JdbcCvelistFetchRepository implements CvelistFetchRepository {

    private static final String TARGETS = """
            select v.vuln_id
            from vulnerability v left join cvelist_fetch f on f.vuln_id = v.vuln_id
            where v.primary_product is null and v.rejected is not true and v.published_at >= :since
              and not exists (select 1 from cna_affected a where a.vuln_id = v.vuln_id)
              and (f.vuln_id is null or f.attempted_at < :retryBefore)
            order by v.published_at desc, v.vuln_id desc
            limit :limit
            """;

    private static final String DELETE_AFFECTED = "delete from cna_affected where vuln_id = :vulnId";

    private static final String INSERT_AFFECTED = """
            insert into cna_affected (vuln_id, position, vendor, product, package_name, default_status, versions,
                                      cpes, package_url)
            values (:vulnId, :position, :vendor, :product, :packageName, :defaultStatus, cast(:versions as jsonb),
                    cast(:cpes as text[]), :packageUrl)
            """;

    private static final String ANSWERED = """
            insert into cvelist_fetch (vuln_id, attempted_at, answered_at) values (:vulnId, :at, :at)
            on conflict (vuln_id) do update set attempted_at = excluded.attempted_at, answered_at = excluded.answered_at
            """;

    private static final String FAILED = """
            insert into cvelist_fetch (vuln_id, attempted_at, failures) values (:vulnId, :at, 1)
            on conflict (vuln_id) do update set attempted_at = excluded.attempted_at,
                                                failures = cvelist_fetch.failures + 1
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<CveId> findTargets(Instant publishedSince, Instant retryBefore, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("since", Timestamp.from(publishedSince))
                .addValue("retryBefore", Timestamp.from(retryBefore))
                .addValue("limit", limit);
        return jdbc.query(TARGETS, params, (rs, rowNum) -> CveId.from(rs.getString("vuln_id")));
    }

    @Override
    public void replaceAffected(CveId cveId, List<CnaAffected> affected) {
        jdbc.update(DELETE_AFFECTED, Map.of("vulnId", cveId.getValue()));
        SqlParameterSource[] rows = IntStream.range(0, affected.size())
                .mapToObj(position -> row(cveId, position, affected.get(position)))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT_AFFECTED, rows);
    }

    @Override
    public void markAnswered(CveId cveId, Instant at) {
        jdbc.update(ANSWERED, Map.of("vulnId", cveId.getValue(), "at", Timestamp.from(at)));
    }

    @Override
    public void markFailed(CveId cveId, Instant at) {
        jdbc.update(FAILED, Map.of("vulnId", cveId.getValue(), "at", Timestamp.from(at)));
    }

    private static SqlParameterSource row(CveId cveId, int position, CnaAffected entry) {
        return new MapSqlParameterSource()
                .addValue("vulnId", cveId.getValue())
                .addValue("position", position)
                .addValue("vendor", entry.getVendor().orElse(null))
                .addValue("product", entry.getProduct().orElse(null))
                .addValue("packageName", entry.getPackageName().orElse(null))
                .addValue("defaultStatus", entry.getDefaultStatus().orElse(null))
                .addValue("versions", CnaVersionsJson.write(entry.getVersions()))
                .addValue("cpes", entry.getCpes().toArray(String[]::new))
                .addValue("packageUrl", entry.getPackageUrl().orElse(null));
    }
}
