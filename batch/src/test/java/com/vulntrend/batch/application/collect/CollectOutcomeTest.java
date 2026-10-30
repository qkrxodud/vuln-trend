package com.vulntrend.batch.application.collect;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.SourceName;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("이번 수집 결과")
class CollectOutcomeTest {

    @Test
    @DisplayName("소스가 모두 끝까지 가져왔으면 실패한 소스가 없다")
    void allLoaded() {
        CollectOutcome outcome = CollectOutcome.of(List.of(
                CollectResult.loaded(SourceName.NVD, 10), CollectResult.loaded(SourceName.KEV, 3)));

        assertThat(outcome.hasFailure()).isFalse();
        assertThat(outcome.failedSources()).isEmpty();
    }

    @Test
    @DisplayName("소스 하나라도 실패하면 실패한 소스가 있고 어느 소스인지 알려 준다")
    void oneFailed() {
        CollectOutcome outcome = CollectOutcome.of(List.of(
                CollectResult.loaded(SourceName.NVD, 10), CollectResult.failed(SourceName.EPSS)));

        assertThat(outcome.hasFailure()).isTrue();
        assertThat(outcome.failedSources()).containsExactly(SourceName.EPSS);
    }

    @Test
    @DisplayName("결과를 만든 뒤 원래 목록이 바뀌어도 이번 수집 결과는 그대로다")
    void resultsAreCopied() {
        List<CollectResult> source = new ArrayList<>(List.of(CollectResult.loaded(SourceName.NVD, 1)));
        CollectOutcome outcome = CollectOutcome.of(source);

        source.add(CollectResult.failed(SourceName.KEV));

        assertThat(outcome.getResults()).hasSize(1);
        assertThat(outcome.hasFailure()).isFalse();
    }
}
