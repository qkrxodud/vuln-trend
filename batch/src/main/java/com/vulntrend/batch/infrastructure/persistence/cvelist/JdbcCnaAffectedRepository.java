package com.vulntrend.batch.infrastructure.persistence.cvelist;

import com.vulntrend.batch.domain.cvelist.CnaAffected;
import com.vulntrend.batch.domain.cvelist.CnaAffectedRepository;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 저장된 CNA affected 읽기(3b 창구). 답을 받은 CVE(cvelist_fetch.answered_at 이 있는 것)만 키로 나오고,
 * 받았지만 affected 가 없던 CVE 는 빈 목록이다. 목록은 position 순서.
 */
@Repository
@RequiredArgsConstructor
public class JdbcCnaAffectedRepository implements CnaAffectedRepository {

    private static final String FIND = """
            select f.vuln_id, a.position, a.vendor, a.product, a.package_name, a.default_status,
                   a.versions::text as versions, a.cpes, a.package_url
            from cvelist_fetch f left join cna_affected a on a.vuln_id = f.vuln_id
            where f.vuln_id = any(cast(:ids as text[])) and f.answered_at is not null
            order by f.vuln_id, a.position
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Map<CveId, List<CnaAffected>> findByCveIds(Collection<CveId> cveIds) {
        if (cveIds.isEmpty()) {
            return Map.of();
        }
        String[] ids = cveIds.stream().map(CveId::getValue).distinct().toArray(String[]::new);
        Map<CveId, List<CnaAffected>> found = new LinkedHashMap<>();
        jdbc.query(FIND, new MapSqlParameterSource("ids", ids), rs -> {
            List<CnaAffected> list = found.computeIfAbsent(CveId.from(rs.getString("vuln_id")),
                    id -> new ArrayList<>());
            if (Objects.nonNull(rs.getObject("position"))) {
                list.add(toAffected(rs));
            }
        });
        Map<CveId, List<CnaAffected>> frozen = new LinkedHashMap<>();
        found.forEach((id, list) -> frozen.put(id, List.copyOf(list)));
        return frozen;
    }

    private static CnaAffected toAffected(ResultSet rs) throws SQLException {
        return CnaAffected.of(rs.getString("vendor"), rs.getString("product"), rs.getString("package_name"),
                rs.getString("default_status"), CnaVersionsJson.read(rs.getString("versions")),
                strings(rs.getArray("cpes")), rs.getString("package_url"));
    }

    private static List<String> strings(Array array) throws SQLException {
        if (Objects.isNull(array)) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }
}
