package com.vulntrend.batch.application.summary;

import com.vulntrend.batch.domain.stat.StatDays;
import com.vulntrend.batch.domain.summary.KoreanSummary;
import com.vulntrend.batch.domain.summary.Summarizer;
import com.vulntrend.batch.domain.summary.SummaryCallBudget;
import com.vulntrend.batch.domain.summary.SummaryOutcome;
import com.vulntrend.batch.domain.summary.SummaryRepository;
import com.vulntrend.batch.domain.summary.SummaryResult;
import com.vulntrend.batch.domain.summary.SummarySource;
import com.vulntrend.batch.domain.vulnerability.Vulnerability;
import com.vulntrend.batch.global.config.SummaryProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 한국어 요약(기획서 6장 7단계, WBS 6.3). 비치명 단계 — 열쇠가 없거나, 하루 상한을 다 썼거나, 도중에 실패해도
 * 경고만 남기고 끝내 다음 단계(출력·게시)가 이어진다.
 * 대상: 트렌드 상위 20건 중 요약이 없는 것(버리거나 거절된 것은 다시 부르지 않는다).
 * 상한 두 개를 한국 시각 자정부터 센다 — 하루 상한(답을 받은 호출만)과 실패 안전 상한(실패만, SummaryCallBudget).
 * 요약을 만들면 무엇이 문제인가 문장의 첫 문장을 제목으로 올린다(P3, 설명에서 만든 임시 제목일 때만).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

    /** 사이트 상위 목록 크기(기획서 6장 "상위 20건") */
    private static final int TOP = 20;

    private final SummaryProperties properties;
    // 열쇠와 모델 이름이 있을 때만 있다(infrastructure/summary 의 SummaryConfiguredCondition)
    private final Optional<Summarizer> summarizer;
    private final SummaryRepository repository;
    private final Clock clock;

    public int summarizeTop() {
        if (summarizer.isEmpty()) {
            log.warn("Korean summary is skipped: SUMMARY_API_KEY or SUMMARY_MODEL is not set. "
                    + "The site shows the English description instead.");
            return 0;
        }
        try {
            return summarizeWith(summarizer.get());
        } catch (RuntimeException e) {
            log.warn("Korean summary step failed ({}); the run continues without new summaries.",
                    e.getClass().getSimpleName());
            return 0;
        }
    }

    private int summarizeWith(Summarizer summaryModel) {
        Instant startOfDay = startOfKoreanDay(clock.instant());
        SummaryCallBudget budget = SummaryCallBudget.of(properties.getDailyLimit(),
                properties.getDailyFailureLimit(), repository.countAnsweredSince(startOfDay),
                repository.countFailedSince(startOfDay));
        if (budget.isUsedUp()) {
            warnUsedUp(budget);
            return 0;
        }
        List<SummarySource> targets = repository.findTopWithoutSummary(TOP);
        Map<SummaryOutcome, Integer> counts = new EnumMap<>(SummaryOutcome.class);
        int called = 0;
        for (SummarySource source : targets) {
            if (budget.isUsedUp()) {
                break;
            }
            SummaryOutcome outcome = summarizeOne(summaryModel, source);
            budget.spend(outcome);
            counts.merge(outcome, 1, Integer::sum);
            called++;
        }
        log.info("Korean summary for {} of the top {} CVEs: {} generated, {} discarded, {} refused, {} failed; "
                        + "{} wait for tomorrow.", called, TOP, count(counts, SummaryOutcome.GENERATED),
                count(counts, SummaryOutcome.DISCARDED), count(counts, SummaryOutcome.REFUSED),
                count(counts, SummaryOutcome.FAILED), targets.size() - called);
        if (budget.isFailureLimitReached() || called < targets.size()) {
            warnUsedUp(budget);
        }
        return count(counts, SummaryOutcome.GENERATED);
    }

    // 실패 안전 상한이 먼저다 — 닿았다면 열쇠·모델 이름·연결을 사람이 확인해야 한다
    private static void warnUsedUp(SummaryCallBudget budget) {
        if (budget.isFailureLimitReached()) {
            log.warn("Korean summary failure limit of {} calls is reached today; summaries stop until tomorrow. "
                    + "Check SUMMARY_API_KEY, SUMMARY_MODEL and the network.", budget.getFailureLimit());
            return;
        }
        log.warn("Korean summary daily limit of {} answered calls is used up; new summaries wait for tomorrow.",
                budget.getAnsweredLimit());
    }

    private SummaryOutcome summarizeOne(Summarizer summaryModel, SummarySource source) {
        SummaryResult result = summaryModel.summarize(source);
        Instant calledAt = clock.instant();
        repository.recordCall(source.getCveId(), result.getOutcome(), result.getModel().orElse(null), calledAt);
        // 버린 이유는 규칙과 걸린 값만 남긴다 — 요약 문장 전체는 남기지 않는다
        result.getDiscardReason().ifPresent(reason -> log.info("Korean summary for {} was discarded ({}: \"{}\").",
                source.getCveId().getValue(), reason.getRule(), reason.getFragment()));
        result.getSummary().ifPresent(summary -> save(source, summary, result.getModel().orElseThrow(), calledAt));
        return result.getOutcome();
    }

    private void save(SummarySource source, KoreanSummary summary, String model, Instant generatedAt) {
        repository.saveSummary(source.getCveId(), summary, model, generatedAt,
                Vulnerability.titleOf(source.getDescription()), Vulnerability.titleOf(summary.getWhat()));
    }

    // 하루 상한의 "하루" = 사이트 달력(한국 시각)
    private static Instant startOfKoreanDay(Instant now) {
        return LocalDate.ofInstant(now, StatDays.CALENDAR).atStartOfDay(StatDays.CALENDAR).toInstant();
    }

    private static int count(Map<SummaryOutcome, Integer> counts, SummaryOutcome outcome) {
        return counts.getOrDefault(outcome, 0);
    }
}
