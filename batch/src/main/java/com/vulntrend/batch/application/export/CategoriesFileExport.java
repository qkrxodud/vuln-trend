package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.CategoriesJson;
import com.vulntrend.batch.application.export.dto.CategoryJson;
import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.category.AttackType;
import com.vulntrend.batch.domain.export.CategoryRepository;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.export.dto.CategoryCount;
import com.vulntrend.batch.domain.product.ProductFamily;
import com.vulntrend.batch.domain.product.ProductNames;
import com.vulntrend.batch.domain.score.TrendListRepository;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import com.vulntrend.batch.domain.stat.StatDays;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * categories.json (사용자 결정 2026-11-01). 공격 유형 10개·제품군 10개를 정해진 순서로 모두 싣는다(건수 0 이어도).
 * - count_7d·count_30d: 오늘 포함 최근 7개·30개 한국 달력일에 발행된 수(recent.json 과 같은 창), kev_count: 그 분류의 KEV 전체 수
 * - top: 그 분류 안 트렌드 순서 상위 최대 20건(목록 줄 모양 그대로). 분류 상위의 CVE 는 상세 파일도 만든다
 * - 게시할지 정하는 화면 지문에는 분류마다 상위의 순서만 넣는다(건수는 계속 바뀌므로 넣지 않는다)
 */
@Component
@RequiredArgsConstructor
public class CategoriesFileExport {

    private static final DataFilePath PATH = DataFilePath.of("categories.json");
    private static final int TOP = 20;
    private static final int WEEK_DAYS = 7;
    private static final int MONTH_DAYS = 30;
    private static final String ATTACK_TYPES = "attack_types:";
    private static final String PRODUCT_FAMILIES = "product_families:";

    private final TrendListRepository trends;
    private final CategoryRepository categories;
    private final SiteDataWriter writer;

    /** 파일을 쓰고, 분류 상위에 든 CVE 를 겹치지 않게 알린다(상세 파일 범위). 건수 구간은 이번 실행 시각, 점수 감쇠는 decayAt(S4) 으로 센다 */
    public Listed write(Instant now, Instant decayAt, ProductNames names) {
        CategoriesJson document = document(now, decayAt, names);
        List<CveId> ids = Stream.concat(document.attackTypes().stream(), document.productFamilies().stream())
                .flatMap(category -> category.top().stream())
                .map(row -> CveId.from(row.id()))
                .distinct()
                .toList();
        return Listed.of(writer.write(PATH, document), ids);
    }

    /** 분류마다 상위의 번호 순서(쓰지 않는다). 열쇠는 "attack_types:rce"·"product_families:os" 꼴, 정해진 분류 순서 */
    public Map<String, List<String>> topIds() {
        Map<String, List<CveId>> byAttackType = trends.topIdsByAttackType(TOP);
        Map<String, List<CveId>> byFamily = trends.topIdsByFamily(TOP);
        Map<String, List<String>> tops = new LinkedHashMap<>();
        Arrays.stream(AttackType.values()).forEach(type -> tops.put(ATTACK_TYPES + type.getKey(),
                values(byAttackType.getOrDefault(type.getKey(), List.of()))));
        Arrays.stream(ProductFamily.values()).forEach(family -> tops.put(PRODUCT_FAMILIES + family.getKey(),
                values(byFamily.getOrDefault(family.getKey(), List.of()))));
        return tops;
    }

    private CategoriesJson document(Instant now, Instant decayAt, ProductNames names) {
        LocalDate today = StatDays.recent(now).getToday();
        Instant weekFrom = today.minusDays(WEEK_DAYS - 1).atStartOfDay(StatDays.CALENDAR).toInstant();
        Instant monthFrom = today.minusDays(MONTH_DAYS - 1).atStartOfDay(StatDays.CALENDAR).toInstant();
        Map<String, CategoryCount> familyCounts = categories.familyCounts(weekFrom, monthFrom, now);
        Map<String, CategoryCount> attackCounts = categories.attackTypeCounts(weekFrom, monthFrom, now);
        Map<String, List<CveId>> byAttackType = trends.topIdsByAttackType(TOP);
        Map<String, List<CveId>> byFamily = trends.topIdsByFamily(TOP);
        Set<CveId> listed = Stream.concat(byAttackType.values().stream(), byFamily.values().stream())
                .flatMap(List::stream).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<CveId, TrendRowJson> rows = trends.listings(listed, decayAt).stream()
                .collect(Collectors.toMap(TrendListing::id, listing -> TrendRows.from(listing, names)));
        List<CategoryJson> attackTypes = Arrays.stream(AttackType.values())
                .map(type -> category(type.getKey(), type.getKoreanName(), attackCounts,
                        rows(byAttackType.getOrDefault(type.getKey(), List.of()), rows)))
                .toList();
        List<CategoryJson> families = Arrays.stream(ProductFamily.values())
                .map(family -> category(family.getKey(), family.getKoreanName(), familyCounts,
                        rows(byFamily.getOrDefault(family.getKey(), List.of()), rows)))
                .toList();
        return new CategoriesJson(attackTypes, families);
    }

    private static CategoryJson category(String key, String label, Map<String, CategoryCount> counts,
            List<TrendRowJson> top) {
        CategoryCount count = counts.getOrDefault(key, new CategoryCount(0, 0, 0));
        return new CategoryJson(key, label, count.count7d(), count.count30d(), count.kevCount(), top);
    }

    /** 상위 번호 순서대로 줄을 놓는다. 그사이 철회돼 읽히지 않은 번호는 빠진다 */
    private static List<TrendRowJson> rows(List<CveId> ids, Map<CveId, TrendRowJson> rows) {
        return ids.stream().filter(rows::containsKey).map(rows::get).toList();
    }

    private static List<String> values(List<CveId> ids) {
        return ids.stream().map(CveId::getValue).toList();
    }
}
