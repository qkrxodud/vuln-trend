package com.vulntrend.batch.infrastructure.persistence.score;

import com.vulntrend.batch.domain.gate.GateQueryRepository;
import com.vulntrend.batch.domain.gate.dto.ProductCount;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Gate G 집계(WBS 9장). 상위 건은 사이트 index 와 같은 순서·대상(TrendOrder). 대표 제품은 표에 있으면 그 표시 이름으로,
 * 없으면 CPE 원문으로 저장되므로(2.3) "표의 표시 이름이 아닌 대표 제품" = 미매핑이다. yml 에 넣으려면 제조사가 필요해서
 * 제조사를 함께 센다(같은 이름이라도 제조사가 다르면 다른 줄).
 */
@Repository
@RequiredArgsConstructor
public class JdbcGateQueryRepository implements GateQueryRepository {

    private static final String TOP = "select vuln_id, primary_product from vulnerability where " + TrendOrder.LISTED
            + " " + TrendOrder.ORDER_BY + " limit :top";
    /** 제조사: 그 제품 이름의 CPE 제조사(여럿이면 사전순 처음), 없으면 KEV 원본의 vendorProject */
    private static final String VENDOR = "coalesce((select min(ap.vendor) from affected_product ap"
            + " where ap.vuln_id = top.vuln_id and ap.product = top.primary_product),"
            + " (select r.payload ->> 'vendorProject' from vulnerability_raw r"
            + " where r.vuln_id = top.vuln_id and r.source = 'kev'))";
    private static final String UNMAPPED = "select vendor, name, count(*) as cves from (select " + VENDOR
            + " as vendor, top.primary_product as name from (" + TOP + ") top"
            + " where top.primary_product is not null"
            + " and not exists (select 1 from product_alias pa where pa.display_name = top.primary_product)) found"
            + " group by vendor, name order by count(*) desc, name, vendor nulls last";
    private static final String WITHOUT_PRODUCT = "select count(*) from (" + TOP + ") top where primary_product is null";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<ProductCount> unmappedProducts(int top) {
        return jdbc.query(UNMAPPED, new MapSqlParameterSource("top", top),
                (rs, rowNum) -> new ProductCount(rs.getString("vendor"), rs.getString("name"), rs.getInt("cves")));
    }

    @Override
    public int withoutProduct(int top) {
        return jdbc.queryForObject(WITHOUT_PRODUCT, new MapSqlParameterSource("top", top), Integer.class);
    }
}
