package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.summary.KoreanSummary;
import com.vulntrend.batch.domain.summary.SummaryOutcome;
import com.vulntrend.batch.domain.summary.SummaryRepository;
import com.vulntrend.batch.domain.summary.SummarySource;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 요약 저장소 흉내. 정해 둔 대상과 오늘 답을 받은·실패한 호출 수로 답하고, 기록·저장한 것을 기억한다 */
public class FakeSummaryRepository implements SummaryRepository {

    private final List<SummarySource> targets;
    private final int answeredToday;
    private final int failedToday;
    private final Map<CveId, SummaryOutcome> calls = new LinkedHashMap<>();
    private final Map<CveId, Saved> saved = new LinkedHashMap<>();
    private Instant answeredSince;
    private Instant failedSince;
    private int requestedTop;

    public FakeSummaryRepository(List<SummarySource> targets, int answeredToday) {
        this(targets, answeredToday, 0);
    }

    public FakeSummaryRepository(List<SummarySource> targets, int answeredToday, int failedToday) {
        this.targets = List.copyOf(targets);
        this.answeredToday = answeredToday;
        this.failedToday = failedToday;
    }

    @Override
    public List<SummarySource> findTopWithoutSummary(int top) {
        this.requestedTop = top;
        return targets;
    }

    @Override
    public int countAnsweredSince(Instant since) {
        this.answeredSince = since;
        return answeredToday;
    }

    @Override
    public int countFailedSince(Instant since) {
        this.failedSince = since;
        return failedToday;
    }

    @Override
    public void recordCall(CveId cveId, SummaryOutcome outcome, String model, Instant calledAt) {
        calls.put(cveId, outcome);
    }

    @Override
    public void saveSummary(CveId cveId, KoreanSummary summary, String model, Instant generatedAt,
            String descriptionTitle, String summaryTitle) {
        saved.put(cveId, new Saved(summary, model, descriptionTitle, summaryTitle));
    }

    public Map<CveId, SummaryOutcome> calls() {
        return Map.copyOf(calls);
    }

    public Map<CveId, Saved> saved() {
        return Map.copyOf(saved);
    }

    public Instant answeredSince() {
        return answeredSince;
    }

    public Instant failedSince() {
        return failedSince;
    }

    public int requestedTop() {
        return requestedTop;
    }

    /** 남긴 요약 한 건 */
    public static class Saved {

        private final KoreanSummary summary;
        private final String model;
        private final String descriptionTitle;
        private final String summaryTitle;

        Saved(KoreanSummary summary, String model, String descriptionTitle, String summaryTitle) {
            this.summary = summary;
            this.model = model;
            this.descriptionTitle = descriptionTitle;
            this.summaryTitle = summaryTitle;
        }

        public KoreanSummary summary() {
            return summary;
        }

        public String model() {
            return model;
        }

        public String descriptionTitle() {
            return descriptionTitle;
        }

        public String summaryTitle() {
            return summaryTitle;
        }
    }
}
