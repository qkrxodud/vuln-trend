package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.export.CveDetailRepository;
import com.vulntrend.batch.domain.export.dto.CveDetailListing;
import com.vulntrend.batch.domain.export.dto.SearchListing;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 상세·검색 조회 흉내. 넣어 둔 검색 줄·상세·상위 번호·제품 표 이름(기본은 없음)을 돌려주고, 상세를 물은 번호와 상위 몇 건을 물었는지 기억한다 */
public class FakeCveDetailRepository implements CveDetailRepository {

    private final List<SearchListing> searchEntries = new ArrayList<>();
    private final Map<CveId, CveDetailListing> details = new LinkedHashMap<>();
    private final List<CveId> topIds = new ArrayList<>();
    private final List<String> standardNames = new ArrayList<>();
    private final List<CveId> askedDetails = new ArrayList<>();
    private final Map<CveId, Instant> published = new LinkedHashMap<>();
    private int askedTop;
    private final Set<Instant> decayTimes = new LinkedHashSet<>();

    public FakeCveDetailRepository search(SearchListing entry) {
        searchEntries.add(entry);
        return this;
    }

    public FakeCveDetailRepository detail(CveDetailListing detail) {
        details.put(detail.row().id(), detail);
        return this;
    }

    public FakeCveDetailRepository top(String... ids) {
        for (String id : ids) {
            topIds.add(CveId.from(id));
        }
        return this;
    }

    public FakeCveDetailRepository standardNames(String... names) {
        standardNames.addAll(List.of(names));
        return this;
    }

    @Override
    public List<String> standardProductNames() {
        return List.copyOf(standardNames);
    }

    @Override
    public List<SearchListing> searchEntries() {
        return List.copyOf(searchEntries);
    }

    /** 발행 시각을 모르므로 검색 줄마다 넣어 둔 발행 시각으로 거른다 */
    @Override
    public List<SearchListing> searchEntriesSince(Instant from) {
        return searchEntries.stream()
                .filter(entry -> published.containsKey(entry.id()) && published.get(entry.id()).compareTo(from) >= 0)
                .toList();
    }

    public FakeCveDetailRepository search(SearchListing entry, Instant publishedAt) {
        published.put(entry.id(), publishedAt);
        return search(entry);
    }

    @Override
    public List<CveId> topIds(int limit) {
        this.askedTop = limit;
        return topIds.stream().limit(limit).toList();
    }

    @Override
    public List<CveDetailListing> details(Collection<CveId> ids, Instant now) {
        askedDetails.addAll(ids);
        decayTimes.add(now);
        return ids.stream().filter(details::containsKey).map(details::get).toList();
    }

    public List<CveId> askedDetails() {
        return askedDetails;
    }

    public int askedTop() {
        return askedTop;
    }

    /** 상세를 물을 때 받은 감쇠 기준 시각들 */
    public Set<Instant> decayTimes() {
        return Set.copyOf(decayTimes);
    }
}
