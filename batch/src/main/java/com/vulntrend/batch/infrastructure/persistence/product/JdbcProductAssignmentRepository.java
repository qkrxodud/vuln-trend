package com.vulntrend.batch.infrastructure.persistence.product;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.product.AffectedProductName;
import com.vulntrend.batch.domain.product.PrimaryProductCandidate;
import com.vulntrend.batch.domain.product.ProductAssignmentRepository;
import com.vulntrend.batch.domain.product.ProductMention;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.infrastructure.nvd.CpeName;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대표 제품·묶음 이름 저장(WBS 2.3). vulnerability.primary_product 와 affected_product.slug 두 칸만 쓴다 —
 * NVD 저장(백엔드 A)의 upsert 는 이 두 칸을 보존한다.
 * CPE 첫 항목은 affected_product 에 순서가 없어서 NVD 원본(vulnerability_raw, source=nvd)에서 문서 순서로 읽는다:
 * configurations[*].nodes[*].cpeMatch[*] 중 vulnerable 이 참인 첫 criteria. 실행 환경(vulnerable=false) 항목은 건너뛴다.
 */
@Repository
@RequiredArgsConstructor
public class JdbcProductAssignmentRepository implements ProductAssignmentRepository {

    private static final String FIND_CANDIDATES = """
            select v.vuln_id, v.primary_product,
                   kev.payload ->> 'vendorProject' as kev_vendor,
                   kev.payload ->> 'product'       as kev_product,
                   jsonb_path_query_first(nvd.payload,
                       '$.configurations[*].nodes[*].cpeMatch[*] ? (@.vulnerable == true).criteria') #>> '{}' as first_cpe
            from vulnerability v
            left join vulnerability_raw kev on kev.vuln_id = v.vuln_id and kev.source = :kev
            left join vulnerability_raw nvd on nvd.vuln_id = v.vuln_id and nvd.source = :nvd
            where coalesce(v.rejected, false) = false
            """;

    private static final String CHANGE_PRIMARY = """
            update vulnerability set primary_product = :primaryProduct where vuln_id = :id
            """;

    private static final String FIND_AFFECTED = """
            select vendor, product, slug from affected_product group by vendor, product, slug
            """;

    private static final String CHANGE_SLUG = """
            update affected_product set slug = cast(:slug as varchar)
            where vendor = :vendor and product = :product and slug is distinct from cast(:slug as varchar)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<PrimaryProductCandidate> findPrimaryCandidates() {
        MapSqlParameterSource sources = new MapSqlParameterSource()
                .addValue("kev", SourceName.KEV.getKey())
                .addValue("nvd", SourceName.NVD.getKey());
        return jdbc.query(FIND_CANDIDATES, sources, JdbcProductAssignmentRepository::toCandidate);
    }

    @Override
    @Transactional
    public int changePrimaryProducts(Map<CveId, Optional<String>> changes) {
        SqlParameterSource[] rows = changes.entrySet().stream()
                .map(change -> new MapSqlParameterSource()
                        .addValue("id", change.getKey().getValue())
                        .addValue("primaryProduct", change.getValue().orElse(null)))
                .toArray(SqlParameterSource[]::new);
        return Arrays.stream(jdbc.batchUpdate(CHANGE_PRIMARY, rows)).sum();
    }

    @Override
    public List<AffectedProductName> findAffectedProductNames() {
        return jdbc.query(FIND_AFFECTED, new MapSqlParameterSource(), JdbcProductAssignmentRepository::toAffectedName)
                .stream()
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    @Transactional
    public int changeSlugs(Map<ProductMention, Optional<String>> changes) {
        SqlParameterSource[] rows = changes.entrySet().stream()
                .map(change -> new MapSqlParameterSource()
                        .addValue("vendor", change.getKey().getVendor())
                        .addValue("product", change.getKey().getProduct())
                        .addValue("slug", change.getValue().orElse(null)))
                .toArray(SqlParameterSource[]::new);
        return Arrays.stream(jdbc.batchUpdate(CHANGE_SLUG, rows)).sum();
    }

    private static PrimaryProductCandidate toCandidate(ResultSet rs, int rowNum) throws SQLException {
        ProductMention kev = ProductMention.from(rs.getString("kev_vendor"), rs.getString("kev_product")).orElse(null);
        ProductMention firstCpe = Optional.ofNullable(rs.getString("first_cpe"))
                .flatMap(CpeName::parse)
                .flatMap(name -> ProductMention.from(name.getVendor(), name.getProduct()))
                .orElse(null);
        return PrimaryProductCandidate.of(CveId.from(rs.getString("vuln_id")), rs.getString("primary_product"), kev,
                firstCpe);
    }

    private static Optional<AffectedProductName> toAffectedName(ResultSet rs, int rowNum) throws SQLException {
        String slug = rs.getString("slug");
        return ProductMention.from(rs.getString("vendor"), rs.getString("product"))
                .map(mention -> AffectedProductName.of(mention, slug));
    }
}
