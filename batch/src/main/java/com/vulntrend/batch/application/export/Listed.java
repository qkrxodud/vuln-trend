package com.vulntrend.batch.application.export;

import com.vulntrend.batch.domain.export.WriteOutcome;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 목록 파일 하나를 쓴 결과: 쓰기 결과와 실린 CVE(순서대로). 상세 파일 생성 범위(P6)를 정할 때 쓴다 */
@Getter
public class Listed {

    private final WriteOutcome outcome;
    private final List<CveId> ids;

    @Builder(access = AccessLevel.PRIVATE)
    private Listed(WriteOutcome outcome, List<CveId> ids) {
        this.outcome = outcome;
        this.ids = ids;
    }

    public static Listed of(WriteOutcome outcome, List<CveId> ids) {
        return Listed.builder().outcome(outcome).ids(List.copyOf(ids)).build();
    }
}
