package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.score.TrendListRepository;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 트렌드 목록 흉내. 넣어 둔 줄을 그대로(순서 유지) 돌려주고, 받은 조건을 기억한다 */
public class FakeTrendListRepository implements TrendListRepository {

    private final List<TrendListing> listings = new ArrayList<>();
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
    public List<TrendListing> publishedBetween(Instant from, Instant to, int limit, Instant now) {
        this.askedFrom = from;
        this.askedTo = to;
        return top(limit, now);
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
