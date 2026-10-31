package com.vulntrend.batch.domain.gate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Gate G 항목 결과(WBS 9장). 확인 필요는 사람이 판단하므로 실패로 치지 않는다 — 웹 gate:check 와 같은 세 갈래·같은 표시 */
@Getter
@RequiredArgsConstructor
public enum GateStatus {

    PASS("✅ 통과"),
    FAIL("❌ 실패"),
    REVIEW("⚠️ 확인 필요");

    private final String label;
}
