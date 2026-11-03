package com.vulntrend.batch.application.cvelist;

import com.vulntrend.batch.domain.cvelist.CnaAffected;
import com.vulntrend.batch.domain.cvelist.CnaAffectedRepository;
import com.vulntrend.batch.domain.cvelist.CnaRecord;
import com.vulntrend.batch.domain.cvelist.CvelistFetchRepository;
import com.vulntrend.batch.domain.cvelist.CvelistSource;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.global.config.CvelistProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CVE.org cvelistV5 제품 보강(S3a, 사용자 승인 2026-11-02). 비치명 보강 단계 — 기획서 6장 실패 규칙의 예외.
 * 대표 제품이 없는 최근 CVE 만 한 건씩 불러 CNA affected 를 저장한다. 실패하면 이전 값이 그대로 남고 실행은 이어진다.
 * 대상 고르기(CvelistFetchRepository): 채운 CVE 는 다시 부르지 않고, 못 채운 것(답이 비었거나 실패)은 재시도 간격 뒤에 다시.
 * 한 실행 상한(maxPerRun)으로 최근 30일 백필을 여러 실행에 나눈다. CVE.org 가 연달아 답하지 못하면 이번 실행은 거기서 멈춘다.
 * 대표 제품·제품군 선정은 하지 않는다(3b, 백엔드 B).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvelistEnrichService {

    private final CvelistProperties properties;
    private final CvelistSource source;
    private final CvelistFetchRepository fetches;
    private final CnaAffectedRepository stored;
    private final Clock clock;

    public int enrichRecent() {
        try {
            return enrich();
        } catch (RuntimeException e) {
            log.warn("Cvelist enrichment failed ({}); the run continues with the product data stored before.",
                    e.getClass().getSimpleName());
            return 0;
        }
    }

    private int enrich() {
        Instant now = clock.instant();
        List<CveId> targets = fetches.findTargets(now.minus(properties.getLookback()),
                now.minus(properties.getRetryAfter()), properties.getMaxPerRun());
        Map<CveId, List<CnaAffected>> current = stored.findByCveIds(targets);
        int fetched = 0;
        int storedCount = 0;
        int failed = 0;
        int failedInRow = 0;
        for (CveId cveId : targets) {
            if (failedInRow >= properties.getMaxConsecutiveFailures()) {
                log.warn("Cvelist enrichment stopped after {} failures in a row; the remaining {} CVEs wait for the "
                        + "next run.", failedInRow, targets.size() - fetched - failed);
                break;
            }
            Optional<CnaRecord> record = fetch(cveId);
            if (record.isEmpty()) {
                failed++;
                failedInRow++;
                continue;
            }
            fetched++;
            failedInRow = 0;
            if (store(record.get(), current.getOrDefault(cveId, List.of()))) {
                storedCount++;
            }
            fetches.markAnswered(cveId, clock.instant());
        }
        log.info("Cvelist enrichment: {} fetched, {} stored, {} failed.", fetched, storedCount, failed);
        return storedCount;
    }

    // 한 CVE 를 받지 못한 것은 그 CVE 만의 실패다(사유 문장은 우리 예외의 고정 문구라 남기지 않아도 실패 수로 충분)
    private Optional<CnaRecord> fetch(CveId cveId) {
        try {
            return Optional.of(source.fetch(cveId));
        } catch (SourceFetchException e) {
            fetches.markFailed(cveId, clock.instant());
            return Optional.empty();
        }
    }

    // 채울 것이 없거나 저장된 것과 같으면 쓰지 않는다
    private boolean store(CnaRecord record, List<CnaAffected> before) {
        if (record.isSameAs(before)) {
            return false;
        }
        if (record.hasAffected()) {
            fetches.replaceAffected(record.getCveId(), record.getAffected());
            return true;
        }
        return false;
    }
}
