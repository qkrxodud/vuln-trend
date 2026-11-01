package com.vulntrend.batch.domain.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("보이는 화면의 지문")
class VisibleFingerprintTest {

    @Test
    @DisplayName("같은 화면 값이면 언제 계산해도 같은 지문이다")
    void sameLinesSameFingerprint() {
        assertThat(VisibleFingerprint.of(List.of("a", "b"))).isEqualTo(VisibleFingerprint.of(List.of("a", "b")));
    }

    @Test
    @DisplayName("값이 하나라도 다르거나 순서가 바뀌면 다른 지문이다")
    void anyDifferenceChangesFingerprint() {
        String base = VisibleFingerprint.of(List.of("a", "b"));

        assertThat(VisibleFingerprint.of(List.of("b", "a"))).isNotEqualTo(base);
        assertThat(VisibleFingerprint.of(List.of("a", "c"))).isNotEqualTo(base);
        assertThat(VisibleFingerprint.of(List.of("ab"))).isNotEqualTo(base);
    }

    @Test
    @DisplayName("지문은 16진수 64글자다")
    void sixtyFourHexDigits() {
        assertThat(VisibleFingerprint.of(List.of())).matches("[0-9a-f]{64}");
    }
}
