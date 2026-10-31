package com.vulntrend.batch.infrastructure.epss;

import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.Collector;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.enrichment.EpssRepository;
import com.vulntrend.batch.domain.enrichment.EpssScore;
import com.vulntrend.batch.domain.vulnerability.SyncWindow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * EPSS 수집(기획서 6장 2단계, WBS 2.2). 전체 점수 파일을 받아 취약점 목록에 있는 CVE 의 악용 확률과 순위를 바꾼다.
 * 수집 구간은 쓰지 않는다. 수집 순회가 NVD·KEV 다음에 부르므로 같은 실행에서 새로 들어온 CVE 에도 확률이 붙는다.
 * 파일에 없는 CVE(갓 발행돼 아직 점수가 없는 것)는 값 없이 둔다 — 0 으로 채우지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EpssCollector implements Collector {

    private final EpssClient client;
    private final EpssRepository repository;

    @Override
    public SourceName source() {
        return SourceName.EPSS;
    }

    @Override
    public CollectResult collect(SyncWindow window) {
        EpssReading reading = EpssReading.keeping(repository.knownIds());
        client.fetch(reading::accept);
        List<EpssScore> scores = reading.finish();
        int applied = repository.applyAll(scores);
        logCoverage(reading.getRows(), applied, repository.vulnerabilityCount());
        return CollectResult.loaded(source(), applied);
    }

    // WBS 2 DoD 의 "EPSS 커버리지 90% 이상"을 실행 기록으로 확인할 수 있게 남긴다
    private static void logCoverage(int fileRows, int applied, int vulnerabilities) {
        long percent = vulnerabilities == 0 ? 0 : Math.round(applied * 100.0 / vulnerabilities);
        log.info("EPSS file had {} scores; {} of {} vulnerabilities now have one ({}%).",
                fileRows, applied, vulnerabilities, percent);
    }
}
