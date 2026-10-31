package com.vulntrend.batch.domain.gate;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** Gate G 항목 하나의 결과: 이름·결과·한 줄 근거·사람이 볼 근거 줄들 */
@Getter
public class GateItem {

    private final String item;
    private final GateStatus status;
    private final String detail;
    private final List<String> evidence;

    @Builder(access = AccessLevel.PRIVATE)
    private GateItem(String item, GateStatus status, String detail, List<String> evidence) {
        this.item = item;
        this.status = status;
        this.detail = detail;
        this.evidence = evidence;
    }

    public static GateItem of(String item, GateStatus status, String detail, List<String> evidence) {
        return GateItem.builder().item(item).status(status).detail(detail).evidence(List.copyOf(evidence)).build();
    }

    public static GateItem of(String item, GateStatus status, String detail) {
        return of(item, status, detail, List.of());
    }
}
