package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.score.TrendListRepository;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 트렌드 목록 흉내. 넣어 둔 줄을 그대로(순서 유지) 돌려주고, 받은 조건을 기억한다. 최근 발행 목록은 따로 넣을 수 있다(없으면 같은 줄) */
public class FakeTrendListRepository implements TrendListRepository {

    private final List<TrendListing> listings = new ArrayList<>();
    private final List<TrendListing> recent = new ArrayList<>();
    private int askedLimit;
    private Instant askedFrom;
    private Instant askedTo;
    private Instant askedNow;

    public FakeTrendListRepository with(TrendListing listing) {
        listings.add(listing);
        return this;
    }

    @Override
    public List<TrendListing> top(int limit, Instant now) {
        this.askedLimit = limit;
        this.askedNow = now;
        return listings.stream().limit(limit).toList();
    }

    @Override
    public List<TrendListing> publishedBetween(Instant from, Instant to, Instant now) {
        this.askedFrom = from;
        this.askedTo = to;
        this.askedNow = now;
        return recent.isEmpty() ? List.copyOf(listings) : List.copyOf(recent);
    }

    /** 최근 발행 목록에만 넣는다(넣은 것이 있으면 발행 구간 조회는 이것만 돌려준다) */
    public FakeTrendListRepository withRecent(TrendListing listing) {
        recent.add(listing);
        return this;
    }

    public int askedLimit() {
        return askedLimit;
    }

    public Instant askedFrom() {
        return askedFrom;
    }

    public Instant askedTo() {
        return askedTo;
    }

    public Instant askedNow() {
        return askedNow;
    }
}
