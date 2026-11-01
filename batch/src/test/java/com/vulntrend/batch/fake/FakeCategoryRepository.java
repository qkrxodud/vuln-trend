package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.export.CategoryRepository;
import com.vulntrend.batch.domain.export.dto.CategoryCount;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 분류별 건수 흉내. 정해 둔 수를 돌려주고, 받은 창의 시작 시각을 기억한다 */
public class FakeCategoryRepository implements CategoryRepository {

    private final Map<String, CategoryCount> families = new LinkedHashMap<>();
    private final Map<String, CategoryCount> attackTypes = new LinkedHashMap<>();
    private Instant askedWeekFrom;
    private Instant askedMonthFrom;

    public FakeCategoryRepository family(String key, int count7d, int count30d, int kevCount) {
        families.put(key, new CategoryCount(count7d, count30d, kevCount));
        return this;
    }

    public FakeCategoryRepository attackType(String key, int count7d, int count30d, int kevCount) {
        attackTypes.put(key, new CategoryCount(count7d, count30d, kevCount));
        return this;
    }

    @Override
    public Map<String, CategoryCount> familyCounts(Instant weekFrom, Instant monthFrom, Instant to) {
        this.askedWeekFrom = weekFrom;
        this.askedMonthFrom = monthFrom;
        return Map.copyOf(families);
    }

    @Override
    public Map<String, CategoryCount> attackTypeCounts(Instant weekFrom, Instant monthFrom, Instant to) {
        return Map.copyOf(attackTypes);
    }

    public Instant askedWeekFrom() {
        return askedWeekFrom;
    }

    public Instant askedMonthFrom() {
        return askedMonthFrom;
    }
}
