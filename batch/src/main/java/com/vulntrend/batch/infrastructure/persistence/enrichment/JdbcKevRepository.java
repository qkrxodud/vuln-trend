package com.vulntrend.batch.infrastructure.persistence.enrichment;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.enrichment.KevEntry;
import com.vulntrend.batch.domain.enrichment.KevMarking;
import com.vulntrend.batch.domain.enrichment.KevRepository;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * KEV 반영(WBS 2.1). vulnerability 에서는 is_kev·kev_added_at·kev_due_date·kev_ransomware 네 칸만 바꾼다 —
 * NVD 저장(백엔드 A)의 upsert 는 이 네 칸을 건드리지 않으므로 서로 덮어쓰지 않는다.
 * 표시를 모두 지운 뒤 목록에 있는 것만 다시 붙여서, CISA 가 목록에서 내린 CVE 도 정리된다. 한 묶음으로 반영한다.
 */
@Repository
@RequiredArgsConstructor
public class JdbcKevRepository implements KevRepository {

    private static final String UPSERT_RAW = """
            insert into vulnerability_raw (vuln_id, source, payload, fetched_at)
            values (:id, :source, cast(:payload as jsonb), :fetchedAt)
            on conflict (vuln_id, source) do update set
                payload    = excluded.payload,
                fetched_at = excluded.fetched_at
            """;

    private static final String CLEAR_ALL = """
            update vulnerability
            set is_kev = false, kev_added_at = null, kev_due_date = null, kev_ransomware = false
            where is_kev
            """;

    private static final String MARK = """
            update vulnerability
            set is_kev = true, kev_added_at = :dateAdded, kev_due_date = :dueDate, kev_ransomware = :ransomware
            where vuln_id = :id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public KevMarking markAll(List<KevEntry> entries, Instant fetchedAt) {
        jdbc.batchUpdate(UPSERT_RAW, entries.stream()
                .map(entry -> rawRow(entry, fetchedAt))
                .toArray(SqlParameterSource[]::new));
        jdbc.update(CLEAR_ALL, new MapSqlParameterSource());
        int[] updated = jdbc.batchUpdate(MARK, entries.stream()
                .map(JdbcKevRepository::markRow)
                .toArray(SqlParameterSource[]::new));
        return toMarking(entries, updated);
    }

    private static KevMarking toMarking(List<KevEntry> entries, int[] updated) {
        int marked = 0;
        List<CveId> missing = new ArrayList<>();
        for (int i = 0; i < updated.length; i++) {
            if (updated[i] > 0) {
                marked++;
                continue;
            }
            missing.add(entries.get(i).getCveId());
        }
        return KevMarking.of(marked, missing);
    }

    private static SqlParameterSource rawRow(KevEntry entry, Instant fetchedAt) {
        return new MapSqlParameterSource()
                .addValue("id", entry.getCveId().getValue())
                .addValue("source", SourceName.KEV.getKey())
                .addValue("payload", entry.getPayload())
                .addValue("fetchedAt", fetchedAt.atOffset(ZoneOffset.UTC));
    }

    private static SqlParameterSource markRow(KevEntry entry) {
        return new MapSqlParameterSource()
                .addValue("id", entry.getCveId().getValue())
                .addValue("dateAdded", entry.getDateAdded())
                .addValue("dueDate", entry.getDueDate().orElse(null))
                .addValue("ransomware", entry.isRansomware());
    }
}
