package com.vulntrend.batch.infrastructure.kev;

import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.Collector;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.enrichment.KevEntry;
import com.vulntrend.batch.domain.enrichment.KevMarking;
import com.vulntrend.batch.domain.enrichment.KevRepository;
import com.vulntrend.batch.domain.vulnerability.SyncWindow;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * KEV 수집(기획서 6장 2단계, WBS 2.1). 목록 전체를 받아 취약점 목록의 실제 악용 표시를 맞춘다. 수집 구간은 쓰지 않는다.
 * 수집 순회가 NVD 다음에 부르므로(SourceName 순서) 같은 실행에서 NVD 가 새로 넣은 CVE 에도 표시가 붙는다.
 * 아직 취약점 목록에 없는 CVE 는 수만 로그로 남긴다 — NVD 다음 증분에서 들어오면 다음 실행에 표시된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KevCollector implements Collector {

    private static final int MISSING_IDS_IN_LOG = 10;

    private final KevClient client;
    private final KevCatalogParser parser;
    private final KevRepository repository;
    private final Clock clock;

    @Override
    public SourceName source() {
        return SourceName.KEV;
    }

    @Override
    public CollectResult collect(SyncWindow window) {
        List<KevEntry> entries = parser.parse(client.fetch());
        KevMarking marking = repository.markAll(entries, clock.instant());
        log.info("KEV listed {} CVEs; {} were marked.", entries.size(), marking.getMarked());
        logMissing(marking);
        return CollectResult.loaded(source(), marking.getMarked());
    }

    private static void logMissing(KevMarking marking) {
        if (marking.getMissing().isEmpty()) {
            return;
        }
        log.info("{} KEV CVEs are not in the vulnerability list yet and wait for NVD (first ones: {}).",
                marking.getMissing().size(), marking.getMissing().stream().limit(MISSING_IDS_IN_LOG).toList());
    }
}
