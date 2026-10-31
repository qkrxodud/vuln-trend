package com.vulntrend.batch.infrastructure.nvd;

import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.Collector;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.CollectedCve;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.RawRecord;
import com.vulntrend.batch.domain.vulnerability.SyncWindow;
import com.vulntrend.batch.domain.vulnerability.Vulnerability;
import com.vulntrend.batch.domain.vulnerability.VulnerabilityRepository;
import com.vulntrend.batch.infrastructure.nvd.dto.NvdPage;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * NVD 수집기(기획서 6장 1단계, WBS 1.4). 구간 안에서 바뀐 CVE 를 쪽마다 읽어 바로 저장한다(1년 백필도 메모리에 쌓지 않는다).
 * - 받은 CVE 는 철회된 것까지 모두 원본으로 보관한다
 * - 살아 있는 CVE 는 upsert, 철회된 CVE 는 이미 있던 건에만 철회 표시
 * - 실패(SourceFetchException)는 그대로 던진다 — 수집 순회가 NVD 를 FAIL 로 기록하고 커서를 그대로 둔다
 * 진행 기록(sync_state)은 이 클래스가 아니라 CollectService 가 맡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NvdCollector implements Collector {

    private final NvdClient client;
    private final NvdCveMapper mapper;
    private final VulnerabilityRepository vulnerabilities;
    private final Clock clock;

    @Override
    public SourceName source() {
        return SourceName.NVD;
    }

    @Override
    public CollectResult collect(SyncWindow window) {
        Instant seenAt = clock.instant();
        List<Tally> pages = new ArrayList<>();
        client.fetch(window, page -> pages.add(store(page, seenAt)));
        Tally total = pages.stream().reduce(Tally.ZERO, Tally::plus);
        log.info("NVD loaded {} records for window {}; {} rejected ({} marked, {} skipped as new); {} unrated ({}%).",
                total.loaded, window, total.rejected, total.marked, total.rejected - total.marked,
                total.unrated, total.unratedPercent());
        return CollectResult.loaded(source(), total.loaded);
    }

    private Tally store(NvdPage page, Instant seenAt) {
        List<CollectedCve> collected = page.vulnerabilities().stream().map(item -> mapper.map(item, seenAt)).toList();
        List<Vulnerability> accepted = collected.stream().map(CollectedCve::getVulnerability).flatMap(Optional::stream)
                .toList();
        List<CveId> rejected = collected.stream().filter(CollectedCve::isRejected).map(CollectedCve::getId).toList();
        vulnerabilities.upsertRaw(collected.stream()
                .map(cve -> RawRecord.of(cve.getId(), SourceName.NVD, cve.getRawPayload(), seenAt))
                .toList());
        int loaded = vulnerabilities.upsertAll(accepted);
        int marked = vulnerabilities.markRejected(rejected);
        int unrated = (int) accepted.stream().filter(v -> v.getCvss().isUnrated()).count();
        return Tally.of(loaded, rejected.size(), marked, unrated);
    }

    /** 쪽 하나의 적재 집계. 실행 기록용 — 빈에 담지 않으려고 쪽마다 새로 만들어 더한다 */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Tally {

        private static final Tally ZERO = new Tally(0, 0, 0, 0);

        private final int loaded;
        private final int rejected;
        private final int marked;
        private final int unrated;

        static Tally of(int loaded, int rejected, int marked, int unrated) {
            return new Tally(loaded, rejected, marked, unrated);
        }

        Tally plus(Tally other) {
            return of(loaded + other.loaded, rejected + other.rejected, marked + other.marked, unrated + other.unrated);
        }

        int unratedPercent() {
            return loaded == 0 ? 0 : unrated * 100 / loaded;
        }
    }
}
