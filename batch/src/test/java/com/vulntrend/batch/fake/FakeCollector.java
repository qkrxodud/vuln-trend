package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.Collector;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.SyncWindow;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.util.ArrayList;
import java.util.List;

/** 수집기 흉내. 정해 둔 건수를 적재했다고 답하거나, 소스 실패를 낸다. 받은 구간을 기억한다 */
public class FakeCollector implements Collector {

    private final SourceName source;
    private final int loadedCount;
    private final boolean failing;
    private final List<SyncWindow> receivedWindows = new ArrayList<>();

    private FakeCollector(SourceName source, int loadedCount, boolean failing) {
        this.source = source;
        this.loadedCount = loadedCount;
        this.failing = failing;
    }

    public static FakeCollector loading(SourceName source, int loadedCount) {
        return new FakeCollector(source, loadedCount, false);
    }

    public static FakeCollector failing(SourceName source) {
        return new FakeCollector(source, 0, true);
    }

    @Override
    public SourceName source() {
        return source;
    }

    @Override
    public CollectResult collect(SyncWindow window) {
        receivedWindows.add(window);
        if (failing) {
            throw new SourceFetchException(source.getKey(), "fake failure");
        }
        return CollectResult.loaded(source, loadedCount);
    }

    public List<SyncWindow> receivedWindows() {
        return List.copyOf(receivedWindows);
    }
}
