package com.vulntrend.batch.infrastructure.epss;

import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.Collector;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.enrichment.EpssRepository;
import com.vulntrend.batch.domain.enrichment.EpssScore;
import com.vulntrend.batch.domain.vulnerability.SyncWindow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * EPSS 수집(기획서 6장 2단계, WBS 2.2). 전체 점수 파일을 받아 취약점 목록에 있는 CVE 의 악용 확률과 순위를 바꾼다.
 * 수집 구간은 쓰지 않는다. 수집 순회가 NVD·KEV 다음에 부르므로 같은 실행에서 새로 들어온 CVE 에도 확률이 붙는다.
 * 2시간마다 확인(사용자 승인 2026-11-01): 오늘(세계 표준시) 날짜의 점수를 이미 반영했으면 내려받지 않고, 내려받은 파일의
 * 점수 날짜가 이미 반영한 날짜와 같으면 다시 반영하지 않는다. 반영하면 그 날짜를 남긴다(날짜가 없는 파일은 남기지 않는다).
 * 파일에 없는 CVE(갓 발행돼 아직 점수가 없는 것)는 값 없이 둔다 — 0 으로 채우지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EpssCollector implements Collector {

    private final EpssClient client;
    private final EpssRepository repository;
    private final Clock clock;

    @Override
    public SourceName source() {
        return SourceName.EPSS;
    }

    @Override
    public CollectResult collect(SyncWindow window) {
        Instant now = clock.instant();
        Optional<LocalDate> appliedDate = repository.appliedScoreDate();
        if (appliedDate.filter(LocalDate.ofInstant(now, ZoneOffset.UTC)::equals).isPresent()) {
            log.info("EPSS scores of {} are already applied; download skipped.", appliedDate.get());
            return CollectResult.loaded(source(), 0);
        }
        EpssReading reading = EpssReading.keeping(repository.knownIds());
        client.fetch(reading::accept);
        List<EpssScore> scores = reading.finish();
        Optional<LocalDate> fileDate = reading.scoreDate();
        if (fileDate.isPresent() && fileDate.equals(appliedDate)) {
            log.info("EPSS file still has the scores of {}; nothing applied.", fileDate.get());
            return CollectResult.loaded(source(), 0);
        }
        int applied = repository.applyAll(scores);
        fileDate.ifPresent(date -> repository.recordScoreDate(date, now));
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
