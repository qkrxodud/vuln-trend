package com.vulntrend.batch.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gate G 점검 결과 표")
class GateReportTest {

    @Test
    @DisplayName("웹 점검과 같은 세 칸 표 뒤에 근거가 있는 항목만 근거 줄을 붙인다")
    void table() {
        GateReport report = GateReport.of(List.of(
                GateItem.of("배치 3일 연속 성공", GateStatus.PASS, "2026-11-01 ~ 2026-11-03"),
                GateItem.of("미매핑 대표 제품", GateStatus.REVIEW, "표에 없는 제품 1개 | 1건", List.of("foo — 1건"))));

        assertThat(report.render()).isEqualTo("""
                | 항목 | 결과 | 근거 |
                |---|---|---|
                | 배치 3일 연속 성공 | ✅ 통과 | 2026-11-01 ~ 2026-11-03 |
                | 미매핑 대표 제품 | ⚠️ 확인 필요 | 표에 없는 제품 1개 / 1건 |

                ### 미매핑 대표 제품
                - foo — 1건""");
    }

    @Test
    @DisplayName("실패가 하나라도 있어야 실패로 끝나고 확인 필요는 실패가 아니다")
    void failureOnlyFromFail() {
        assertThat(GateReport.of(List.of(GateItem.of("a", GateStatus.REVIEW, "x"))).hasFailure()).isFalse();
        assertThat(GateReport.of(List.of(GateItem.of("a", GateStatus.REVIEW, "x"),
                GateItem.of("b", GateStatus.FAIL, "y"))).hasFailure()).isTrue();
    }
}
