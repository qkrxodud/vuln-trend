package com.vulntrend.batch.domain.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * Gate G 배치 쪽 점검 결과 묶음(WBS 9장). 표 모양·결과 표시·종료 규칙은 웹 gate:check(web/scripts/gate/report.ts)와 같다 —
 * 두 점검 결과를 나란히 붙여 볼 수 있게. 확인 필요는 사람이 판단하므로 실패로 치지 않는다.
 */
public class GateReport {

    private static final String HEADER = "| 항목 | 결과 | 근거 |\n|---|---|---|";

    @Getter
    private final List<GateItem> items;

    @Builder(access = AccessLevel.PRIVATE)
    private GateReport(List<GateItem> items) {
        this.items = items;
    }

    public static GateReport of(List<GateItem> items) {
        return GateReport.builder().items(List.copyOf(items)).build();
    }

    /** 실패가 하나라도 있으면 true — 종료 코드 1 */
    public boolean hasFailure() {
        return items.stream().anyMatch(item -> item.getStatus() == GateStatus.FAIL);
    }

    /** 웹 gate:check 와 같은 모양의 표(| 항목 | 결과 | 근거 |)와 근거가 있는 항목의 근거 줄 */
    public String render() {
        String rows = items.stream()
                .map(item -> "| " + cell(item.getItem()) + " | " + item.getStatus().getLabel() + " | "
                        + cell(item.getDetail()) + " |")
                .collect(Collectors.joining("\n"));
        List<String> sections = new ArrayList<>(List.of(HEADER + "\n" + rows));
        items.stream()
                .filter(item -> item.getEvidence().size() > 0)
                .map(item -> Stream.concat(Stream.of("### " + item.getItem()),
                        item.getEvidence().stream().map(line -> "- " + line)).collect(Collectors.joining("\n")))
                .forEach(sections::add);
        return String.join("\n\n", sections);
    }

    private static String cell(String text) {
        return text.replace("|", "/").replace("\n", " ");
    }
}
