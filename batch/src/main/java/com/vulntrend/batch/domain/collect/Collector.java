package com.vulntrend.batch.domain.collect;

import com.vulntrend.batch.domain.vulnerability.SyncWindow;

/**
 * 수집기 창구. 소스별 구현(NvdCollector, KevCollector, EpssCollector …)이 빈으로 등록되면 수집 순회가 모두 돈다.
 * 실패는 SourceFetchException(global/error/exception)으로 알린다 — 그 소스만 실패로 기록되고 커서는 그대로 남는다.
 * 구간을 쓰지 않는 소스(KEV·EPSS 전체 내려받기)는 window 를 무시해도 된다.
 */
public interface Collector {

    SourceName source();

    CollectResult collect(SyncWindow window);
}
