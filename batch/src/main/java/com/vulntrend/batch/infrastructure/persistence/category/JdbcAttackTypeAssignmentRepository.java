package com.vulntrend.batch.infrastructure.persistence.category;

import com.vulntrend.batch.domain.category.AttackType;
import com.vulntrend.batch.domain.category.AttackTypeAssignmentRepository;
import com.vulntrend.batch.domain.category.CveWeaknesses;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * 약점 번호는 vulnerability_raw(nvd).payload.weaknesses[].description[].value 에서 읽는다(수집기는 바꾸지 않는다).
 * 배열이 아닌 모양과 value 가 없는 칸은 건너뛴다. 원본이 없는 CVE(KEV 만 있는 것 등)는 약점이 없는 것으로 읽는다.
 * 실DB 약 40만 건을 한 번에 훑는다(2026-11-01 실측 약 10초).
 */
@Repository
@RequiredArgsConstructor
public class JdbcAttackTypeAssignmentRepository implements AttackTypeAssignmentRepository {

    private static final String FIND = """
            with cwes as (
              select r.vuln_id, array_agg(d.value ->> 'value') as cwe_ids
              from vulnerability_raw r
              cross join lateral jsonb_array_elements(case when jsonb_typeof(r.payload -> 'weaknesses') = 'array'
                  then r.payload -> 'weaknesses' else '[]'::jsonb end) as w(value)
              cross join lateral jsonb_array_elements(case when jsonb_typeof(w.value -> 'description') = 'array'
                  then w.value -> 'description' else '[]'::jsonb end) as d(value)
              where r.source = 'nvd' and d.value ->> 'value' is not null
              group by r.vuln_id)
            select v.vuln_id, v.attack_types, c.cwe_ids
            from vulnerability v left join cwes c on c.vuln_id = v.vuln_id
            """;

    private static final String UPDATE = """
            update vulnerability set attack_types = cast(:types as text[]) where vuln_id = :vulnId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<CveWeaknesses> findWeaknesses() {
        return jdbc.query(FIND, this::toWeaknesses);
    }

    @Override
    public int changeAttackTypes(Map<CveId, List<AttackType>> changes) {
        if (changes.isEmpty()) {
            return 0;
        }
        SqlParameterSource[] rows = changes.entrySet().stream()
                .map(change -> new MapSqlParameterSource()
                        .addValue("vulnId", change.getKey().getValue())
                        .addValue("types", change.getValue().stream().map(AttackType::getKey).toArray(String[]::new)))
                .toArray(SqlParameterSource[]::new);
        return Arrays.stream(jdbc.batchUpdate(UPDATE, rows)).sum();
    }

    private CveWeaknesses toWeaknesses(ResultSet rs, int rowNum) throws SQLException {
        return CveWeaknesses.of(CveId.from(rs.getString("vuln_id")), strings(rs.getArray("cwe_ids")),
                strings(rs.getArray("attack_types")));
    }

    private static List<String> strings(Array array) throws SQLException {
        if (Objects.isNull(array)) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }
}
