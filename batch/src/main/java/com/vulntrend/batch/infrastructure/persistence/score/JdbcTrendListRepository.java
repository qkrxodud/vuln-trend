package com.vulntrend.batch.infrastructure.persistence.score;

import com.vulntrend.batch.domain.remedy.dto.RemedyListing;
import com.vulntrend.batch.domain.remedy.dto.TaggedLink;
import com.vulntrend.batch.domain.score.TrendListRepository;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.Severity;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 트렌드 목록 조회(WBS 4.2 index·recent). 순서·대상은 TrendOrder, 점수 구성은 TrendScoreSql 조각을 같은 실행 시각으로 계산한다
 * — 정렬에 쓰는 저장된 trend_score 와 같은 식이다. 요약(summary.what)은 있으면 붙인다.
 */
@Repository
@RequiredArgsConstructor
public class JdbcTrendListRepository implements TrendListRepository {

    /**
     * 조치 정보 열(remedy(rs) 와 짝). 수집기를 바꾸지 않고 저장된 원본에서 읽는다.
     * - 대표 제품의 취약 범위: 영향 제품 중 제품 원문이 대표 제품과 같거나, 표에서 같은 slug 의 표시 이름이 대표 제품인 줄(제조사·제품 순)
     * - NVD 참고 링크: vulnerability_raw(nvd).payload.references 의 주소와 태그(원본 순서, 태그는 '|' 로 이어 한 칸에)
     * - KEV 요구 조치: vulnerability_raw(kev).payload.requiredAction
     */
    private static final String REMEDY_COLUMNS = """
            (select array_agg(ap.vulnerable_range order by ap.vendor, ap.product) from affected_product ap
              where ap.vuln_id = v.vuln_id and ap.vulnerable_range is not null
                and (ap.product = v.primary_product or exists (select 1 from product_alias pa
                     where pa.slug = ap.slug and pa.display_name = v.primary_product))) as primary_ranges,
            (select array_agg(ref.value ->> 'url' order by ref.idx) from vulnerability_raw r
               cross join lateral jsonb_array_elements(case when jsonb_typeof(r.payload -> 'references') = 'array'
                   then r.payload -> 'references' else '[]'::jsonb end) with ordinality as ref(value, idx)
              where r.vuln_id = v.vuln_id and r.source = 'nvd') as nvd_reference_urls,
            (select array_agg(coalesce((select string_agg(t, '|') from jsonb_array_elements_text(
                   case when jsonb_typeof(ref.value -> 'tags') = 'array' then ref.value -> 'tags' else '[]'::jsonb end) t),
                   '') order by ref.idx) from vulnerability_raw r
               cross join lateral jsonb_array_elements(case when jsonb_typeof(r.payload -> 'references') = 'array'
                   then r.payload -> 'references' else '[]'::jsonb end) with ordinality as ref(value, idx)
              where r.vuln_id = v.vuln_id and r.source = 'nvd') as nvd_reference_tags,
            (select r.payload ->> 'requiredAction' from vulnerability_raw r
              where r.vuln_id = v.vuln_id and r.source = 'kev') as kev_required_action""";
    private static final String TAG_SEPARATOR = "\\|";

    /** 목록 줄 하나의 열들(listing(rs) 와 짝). 상세 조회도 같은 열을 읽는다 */
    static final String COLUMNS = "v.vuln_id, v.title, v.is_kev, v.kev_ransomware, v.kev_due_date, v.kev_added_at, "
            + "v.poc_count, v.mention_count, v.epss_score, v.epss_percentile, v.cvss_score, v.cvss_version, "
            + "v.cvss_severity, v.primary_product, v.product_family, v.attack_types, v.published_at, "
            + "s.what as summary_what, "
            + TrendScoreSql.BREAKDOWN_COLUMNS + ", " + REMEDY_COLUMNS;
    static final String FROM = " from vulnerability v left join summary s on s.vuln_id = v.vuln_id where "
            + TrendOrder.LISTED;
    private static final String SELECT = "select " + COLUMNS + FROM;
    private static final String TOP = SELECT + " " + TrendOrder.ORDER_BY + " limit :limit";
    /** 최근 발행 목록(recent.json): 구간 안 전부, 최근 발행 순 → 번호 순. 트렌드 순서가 아니다 */
    /** 분류마다 상위: 한 번 훑어 분류별 순번을 매긴다(분류마다 따로 묻는 것보다 2026-11-01 실측 약 열 배 빠르다) */
    private static final String RANKED = " order by trend_score desc nulls last, published_at desc nulls last, vuln_id) as rank ";
    private static final String TOP_IDS_BY_FAMILY = "select key, vuln_id from (select product_family as key, vuln_id, "
            + "row_number() over (partition by product_family" + RANKED + "from vulnerability where " + TrendOrder.LISTED
            + " and product_family is not null) ranked where rank <= :limit order by key, rank";
    private static final String TOP_IDS_BY_ATTACK_TYPE = "select key, vuln_id from (select attack_type as key, vuln_id, "
            + "row_number() over (partition by attack_type" + RANKED + "from vulnerability cross join lateral "
            + "unnest(attack_types) as attack_type where " + TrendOrder.LISTED + ") ranked where rank <= :limit "
            + "order by key, rank";
    private static final String LISTINGS = SELECT + " and v.vuln_id in (:ids)";
    private static final String PUBLISHED_BETWEEN = SELECT + " and published_at >= :from and published_at < :to "
            + "order by v.published_at desc, v.vuln_id";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<TrendListing> top(int limit, Instant now) {
        return jdbc.query(TOP, params(limit, now), JdbcTrendListRepository::listing);
    }

    @Override
    public Map<String, List<CveId>> topIdsByFamily(int limit) {
        return topIds(TOP_IDS_BY_FAMILY, limit);
    }

    @Override
    public Map<String, List<CveId>> topIdsByAttackType(int limit) {
        return topIds(TOP_IDS_BY_ATTACK_TYPE, limit);
    }

    @Override
    public List<TrendListing> listings(Collection<CveId> ids, Instant now) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query(LISTINGS, params(ids.size(), now).addValue("ids", ids.stream().map(CveId::getValue).toList()),
                JdbcTrendListRepository::listing);
    }

    private Map<String, List<CveId>> topIds(String sql, int limit) {
        Map<String, List<CveId>> tops = new LinkedHashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("limit", limit), rs -> {
            tops.computeIfAbsent(rs.getString("key"), key -> new ArrayList<>()).add(CveId.from(rs.getString("vuln_id")));
        });
        return tops;
    }

    @Override
    public List<TrendListing> publishedBetween(Instant from, Instant to, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("now", now.atOffset(ZoneOffset.UTC))
                .addValue("from", from.atOffset(ZoneOffset.UTC))
                .addValue("to", to.atOffset(ZoneOffset.UTC));
        return jdbc.query(PUBLISHED_BETWEEN, params, JdbcTrendListRepository::listing);
    }

    private static MapSqlParameterSource params(int limit, Instant now) {
        return new MapSqlParameterSource().addValue("limit", limit).addValue("now", now.atOffset(ZoneOffset.UTC));
    }

    static TrendListing listing(ResultSet rs, int rowNum) throws SQLException {
        return new TrendListing(CveId.from(rs.getString("vuln_id")), rs.getString("title"),
                TrendScoreSql.breakdown(rs), rs.getBoolean("is_kev"), rs.getBoolean("kev_ransomware"),
                rs.getObject("kev_due_date", LocalDate.class), rs.getObject("kev_added_at", LocalDate.class),
                rs.getInt("poc_count"), rs.getInt("mention_count"),
                rs.getBigDecimal("epss_score"), rs.getBigDecimal("epss_percentile"), rs.getBigDecimal("cvss_score"),
                rs.getString("cvss_version"), severity(rs.getString("cvss_severity")), rs.getString("primary_product"),
                rs.getString("summary_what"),
                Optional.ofNullable(rs.getObject("published_at", OffsetDateTime.class))
                        .map(OffsetDateTime::toInstant).orElse(null), remedy(rs), rs.getString("product_family"),
                texts(rs, "attack_types"));
    }

    private static RemedyListing remedy(ResultSet rs) throws SQLException {
        List<String> urls = texts(rs, "nvd_reference_urls");
        List<String> tags = texts(rs, "nvd_reference_tags");
        List<TaggedLink> links = IntStream.range(0, Math.min(urls.size(), tags.size()))
                .filter(i -> Objects.nonNull(urls.get(i)))
                .mapToObj(i -> new TaggedLink(urls.get(i), Arrays.stream(tags.get(i).split(TAG_SEPARATOR))
                        .filter(Predicate.not(String::isEmpty)).toList()))
                .toList();
        return new RemedyListing(texts(rs, "primary_ranges"), links, rs.getString("kev_required_action"));
    }

    private static List<String> texts(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (Objects.isNull(array)) {
            return List.of();
        }
        return Arrays.stream((Object[]) array.getArray()).map(value -> (String) value).toList();
    }

    private static Severity severity(String stored) {
        return Optional.ofNullable(stored).map(Severity::valueOf).orElse(Severity.UNRATED);
    }
}
