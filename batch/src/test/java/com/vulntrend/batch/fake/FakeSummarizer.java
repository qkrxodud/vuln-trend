package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.summary.Summarizer;
import com.vulntrend.batch.domain.summary.SummaryResult;
import com.vulntrend.batch.domain.summary.SummarySource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Claude 흉내. 정해 둔 규칙으로 답하고, 요약을 부탁받은 원문을 순서대로 기억한다 */
public class FakeSummarizer implements Summarizer {

    private final Function<SummarySource, SummaryResult> answer;
    private final List<SummarySource> asked = new ArrayList<>();

    public FakeSummarizer(Function<SummarySource, SummaryResult> answer) {
        this.answer = answer;
    }

    @Override
    public SummaryResult summarize(SummarySource source) {
        asked.add(source);
        return answer.apply(source);
    }

    public List<SummarySource> asked() {
        return List.copyOf(asked);
    }
}
