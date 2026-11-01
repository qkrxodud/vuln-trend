package com.vulntrend.batch.infrastructure.persistence.score;

import com.vulntrend.batch.domain.export.CveDetailRepository;
import com.vulntrend.batch.domain.export.dto.AffectedListing;
import com.vulntrend.batch.domain.export.dto.CveDetailListing;
import com.vulntrend.batch.domain.export.dto.ReferenceListing;
import com.vulntrend.batch.domain.export.dto.SearchListing;
import com.vulntrend.batch.domain.export.dto.SummaryListing;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 상세·검색 파일용 조회(WBS 4.2). 목록 줄은 JdbcTrendListRepository 와 같은 열·같은 점수식으로 읽는다.
 * - 검색 색인: 철회 제외 전체, 최근 발행 순(발행일 없으면 맨 뒤) → 번호
 * - 영향 제품: 제조사·제품 순. 표시 이름은 product_alias 에서 같은 slug 의 이름
 * - 제품 이름은 저장 원문 그대로 읽는다. 사이트에 보일 이름은 출력이 정한다(ProductNames)
 * - 참고 링크: 출처 순(vendor → nvd → news → poc) → 주소 순
 */
@Repository
@RequiredArgsConstructor
public class JdbcCveDetailRepository implements CveDetailRepository {

    private static final String SEARCH_ENTRIES = "select vuln_id, title, primary_product from vulnerability where "
            + TrendOrder.LISTED + " order by published_at desc nulls last, vuln_id";
    private static final String SEARCH_ENTRIES_SINCE = "select vuln_id, title, primary_product from vulnerability where "
            + TrendOrder.LISTED + " and published_at >= :from order by published_at desc, vuln_id";
    private static final String STANDARD_NAMES = "select distinct display_name from product_alias order by display_name";
    private static final String TOP_IDS = "select vuln_id from vulnerability where " + TrendOrder.LISTED + " "
            + TrendOrder.ORDER_BY + " limit :limit";
    private static final String DETAILS = "select " + JdbcTrendListRepository.COLUMNS
            + ", v.description, v.last_modified_at, s.vuln_id as summary_id, s.who as summary_who, "
            + "s.action as summary_action, s.model as summary_model, s.generated_at as summary_generated_at"
            + JdbcTrendListRepository.FROM + " and v.vuln_id in (:ids)";
    private static final String AFFECTED = """
            select ap.vuln_id, ap.vendor, ap.product, ap.slug, ap.ecosystem, ap.vulnerable_range, ap.fixed_version,
                   (select pa.display_name from product_alias pa where pa.slug = ap.slug
                    order by pa.display_name limit 1) as display_name
            from affected_product ap
            where ap.vuln_id in (:ids)
            order by ap.vuln_id, ap.vendor, ap.product
            """;
    private static final String REFERENCES = """
            select vuln_id, url, source, title
            from reference_link
            where vuln_id in (:ids)
            order by vuln_id,
                     case source when 'vendor' then 0 when 'nvd' then 1 when 'news' then 2 when 'poc' then 3 else 4 end,
                     url
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<SearchListing> searchEntries() {
        return jdbc.query(SEARCH_ENTRIES, (rs, rowNum) -> new SearchListing(CveId.from(rs.getString("vuln_id")),
                rs.getString("title"), rs.getString("primary_product")));
    }

    @Override
    public List<SearchListing> searchEntriesSince(Instant from) {
        return jdbc.query(SEARCH_ENTRIES_SINCE, new MapSqlParameterSource("from", from.atOffset(ZoneOffset.UTC)),
                (rs, rowNum) -> new SearchListing(CveId.from(rs.getString("vuln_id")), rs.getString("title"),
                        rs.getString("primary_product")));
    }

    @Override
    public List<String> standardProductNames() {
        return jdbc.query(STANDARD_NAMES, (rs, rowNum) -> rs.getString("display_name"));
    }

    @Override
    public List<CveId> topIds(int limit) {
        return jdbc.query(TOP_IDS, new MapSqlParameterSource("limit", limit),
                (rs, rowNum) -> CveId.from(rs.getString("vuln_id")));
    }

    @Override
    public List<CveDetailListing> details(Collection<CveId> ids, Instant now) {
        if (ids.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", ids.stream().map(CveId::getValue).toList())
                .addValue("now", now.atOffset(ZoneOffset.UTC));
        Map<String, List<AffectedListing>> affected = grouped(AFFECTED, params, rs -> new AffectedListing(
                rs.getString("vendor"), rs.getString("product"), rs.getString("display_name"), rs.getString("slug"),
                rs.getString("ecosystem"), rs.getString("vulnerable_range"), rs.getString("fixed_version")));
        Map<String, List<ReferenceListing>> references = grouped(REFERENCES, params,
                rs -> new ReferenceListing(rs.getString("url"), rs.getString("source"), rs.getString("title")));
        return jdbc.query(DETAILS, params, (rs, rowNum) -> {
            TrendListing row = JdbcTrendListRepository.listing(rs, rowNum);
            String id = row.id().getValue();
            return new CveDetailListing(row, rs.getString("description"), instant(rs, "last_modified_at"), summary(rs),
                    affected.getOrDefault(id, List.of()), references.getOrDefault(id, List.of()));
        });
    }

    private static SummaryListing summary(ResultSet rs) throws SQLException {
        if (Objects.isNull(rs.getString("summary_id"))) {
            return null;
        }
        return new SummaryListing(rs.getString("summary_what"), rs.getString("summary_who"),
                rs.getString("summary_action"), rs.getString("summary_model"), instant(rs, "summary_generated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return Optional.ofNullable(rs.getObject(column, OffsetDateTime.class)).map(OffsetDateTime::toInstant)
                .orElse(null);
    }

    private <T> Map<String, List<T>> grouped(String sql, MapSqlParameterSource params, RowReader<T> reader) {
        Map<String, List<T>> grouped = new HashMap<>();
        jdbc.query(sql, params, (ResultSet rs) -> {
            grouped.computeIfAbsent(rs.getString("vuln_id"), id -> new ArrayList<>()).add(reader.read(rs));
        });
        return grouped;
    }

    /** 한 줄을 값 하나로 읽는다 */
    @FunctionalInterface
    private interface RowReader<T> {
        T read(ResultSet rs) throws SQLException;
    }
}
