package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.cvelist.CnaAffected;
import com.vulntrend.batch.domain.cvelist.CvelistFetchRepository;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CVE.org 보강 상태 흉내. 넣어 둔 대상을 limit 만큼 돌려주고, 받은 조건·바꾼 affected·답 받음·실패를 기억한다.
 * 바꾼 affected 는 함께 넘긴 저장 흉내(FakeCnaAffectedRepository)에도 넣는다.
 */
public class FakeCvelistFetchRepository implements CvelistFetchRepository {

    private final FakeCnaAffectedRepository stored;
    private final List<CveId> targets = new ArrayList<>();
    private final Map<CveId, List<CnaAffected>> replaced = new LinkedHashMap<>();
    private final Map<CveId, Instant> answered = new LinkedHashMap<>();
    private final Map<CveId, Instant> failed = new LinkedHashMap<>();
    private Instant publishedSince;
    private Instant retryBefore;
    private int limit;
    private boolean broken;

    public FakeCvelistFetchRepository(FakeCnaAffectedRepository stored) {
        this.stored = stored;
    }

    public FakeCvelistFetchRepository targeting(CveId... ids) {
        targets.addAll(List.of(ids));
        return this;
    }

    /** 대상 조회가 깨진 저장소 */
    public FakeCvelistFetchRepository broken() {
        broken = true;
        return this;
    }

    @Override
    public List<CveId> findTargets(Instant publishedSince, Instant retryBefore, int limit) {
        if (broken) {
            throw new IllegalStateException("storage is down");
        }
        this.publishedSince = publishedSince;
        this.retryBefore = retryBefore;
        this.limit = limit;
        return targets.stream().limit(limit).toList();
    }

    @Override
    public void replaceAffected(CveId cveId, List<CnaAffected> affected) {
        replaced.put(cveId, affected);
        stored.with(cveId, affected);
    }

    @Override
    public void markAnswered(CveId cveId, Instant at) {
        answered.put(cveId, at);
    }

    @Override
    public void markFailed(CveId cveId, Instant at) {
        failed.put(cveId, at);
    }

    public Map<CveId, List<CnaAffected>> replaced() {
        return replaced;
    }

    public Map<CveId, Instant> answered() {
        return answered;
    }

    public Map<CveId, Instant> failed() {
        return failed;
    }

    public Instant publishedSince() {
        return publishedSince;
    }

    public Instant retryBefore() {
        return retryBefore;
    }

    public int limit() {
        return limit;
    }
}
