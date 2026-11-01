package com.vulntrend.batch.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("요약을 버린 이유")
class DiscardReasonTest {

    @Test
    @DisplayName("걸린 규칙과 걸린 값을 함께 든다")
    void ruleAndFragment() {
        DiscardReason reason = DiscardReason.of(DiscardRule.UNKNOWN_VERSION, "2.17.1");

        assertThat(reason.getRule()).isEqualTo(DiscardRule.UNKNOWN_VERSION);
        assertThat(reason.getFragment()).isEqualTo("2.17.1");
    }

    @Test
    @DisplayName("걸린 값은 실행 기록 한 줄에 맞게 줄바꿈 없이 60자까지만 든다")
    void fragmentFitsOneLogLine() {
        DiscardReason reason = DiscardReason.of(DiscardRule.COMMAND, "sudo\nrm\t" + "x".repeat(100));

        assertThat(reason.getFragment()).hasSize(60).startsWith("sudo rm x").doesNotContain("\n", "\t");
    }
}
