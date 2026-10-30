package com.vulntrend.batch.application.collect;

import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.SourceName;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 이번 실행의 수집 결과 묶음. 러너는 hasFailure() 하나만 묻는다 */
@Getter
public class CollectOutcome {

    private final List<CollectResult> results;

    @Builder(access = AccessLevel.PRIVATE)
    private CollectOutcome(List<CollectResult> results) {
        this.results = List.copyOf(results);
    }

    public static CollectOutcome of(Collection<CollectResult> results) {
        return CollectOutcome.builder().results(List.copyOf(results)).build();
    }

    public boolean hasFailure() {
        return results.stream().anyMatch(CollectResult::isFailed);
    }

    public List<SourceName> failedSources() {
        return results.stream().filter(CollectResult::isFailed).map(CollectResult::getSource).toList();
    }
}
