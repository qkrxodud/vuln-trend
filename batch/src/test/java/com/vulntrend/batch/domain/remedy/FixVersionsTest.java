package com.vulntrend.batch.domain.remedy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("업데이트할 버전 — 취약 범위의 미만 상한")
class FixVersionsTest {

    @Nested
    @DisplayName("범위 하나에서")
    class OneRange {

        @Test
        @DisplayName("여러 갈래면 갈래마다 미만 상한을 범위에 나온 순서로 뽑는다")
        void everyBranch() {
            assertThat(FixVersions.from(">=19.2.0, <19.2.8 || >=19.3.0, <19.3.5 || >=19.4.0, <19.4.2"))
                    .containsExactly("19.2.8", "19.3.5", "19.4.2");
        }

        @Test
        @DisplayName("상한만 있는 범위도 뽑는다")
        void upperOnly() {
            assertThat(FixVersions.from("< 130.0.6723.58")).containsExactly("130.0.6723.58");
        }

        @Test
        @DisplayName("이하 상한은 고친 버전을 알 수 없어 뽑지 않는다")
        void inclusiveUpperIsUnknown() {
            assertThat(FixVersions.from(">=8.0.0, <=8.0.39 || >=8.4.0, <8.4.3")).containsExactly("8.4.3");
        }

        @Test
        @DisplayName("특정 버전만 적힌 갈래는 고친 버전이 아니다")
        void exactVersionIsNotFix() {
            assertThat(FixVersions.from("=2.7.1-rc1 || =2.8")).isEmpty();
        }

        @Test
        @DisplayName("같은 버전이 두 번 나오면 한 번만 싣는다")
        void noDuplicates() {
            assertThat(FixVersions.from("<7.0.1 || >=7.0.0, <7.0.1")).containsExactly("7.0.1");
        }

        @ParameterizedTest(name = "범위 \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {">=16.1.1", "   "})
        @DisplayName("범위가 없거나 상한이 없으면 모른다")
        void unknown(String range) {
            assertThat(FixVersions.from(range)).isEmpty();
        }
    }

    @Test
    @DisplayName("여러 영향 제품의 범위를 합치면 순서대로 겹치지 않게 모은다")
    void acrossProducts() {
        assertThat(FixVersions.fromAll(List.of("<1.2.3 || >=2.0, <2.0.5", "<2.0.5", "=3.0")))
                .containsExactly("1.2.3", "2.0.5");
    }
}
