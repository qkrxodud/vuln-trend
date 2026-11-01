package com.vulntrend.batch.domain.category;

import static com.vulntrend.batch.domain.category.AttackType.INJECTION;
import static com.vulntrend.batch.domain.category.AttackType.MEMORY;
import static com.vulntrend.batch.domain.category.AttackType.OTHER;
import static com.vulntrend.batch.domain.category.AttackType.RCE;
import static com.vulntrend.batch.domain.category.AttackType.XSS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("약점 번호(CWE)로 공격 유형 나누기")
class AttackTypesTest {

    @ParameterizedTest(name = "{0} 은 {1}")
    @CsvSource({
            "CWE-79, xss", "CWE-89, injection", "CWE-502, rce", "CWE-416, memory", "CWE-862, auth",
            "CWE-200, info", "CWE-400, dos", "CWE-22, path", "CWE-434, path", "CWE-918, request_forgery",
            "CWE-352, request_forgery", "CWE-20, other"})
    @DisplayName("흔한 약점은 하나의 공격 유형으로 나뉜다")
    void commonWeakness(String cwe, String key) {
        assertThat(AttackTypes.classify(List.of(cwe))).extracting(AttackType::getKey).containsExactly(key);
    }

    @Test
    @DisplayName("명령·코드 주입은 주입이면서 원격 코드 실행이다")
    void commandInjectionIsBoth() {
        assertThat(AttackTypes.classify(List.of("CWE-78"))).containsExactly(RCE, INJECTION);
        assertThat(AttackTypes.classify(List.of("CWE-94"))).containsExactly(RCE, INJECTION);
    }

    @Test
    @DisplayName("약점이 여럿이면 유형을 합치되 겹치지 않고 정해진 순서로 낸다")
    void mergesInFixedOrder() {
        assertThat(AttackTypes.classify(List.of("CWE-79", "CWE-89", "CWE-79", "CWE-416")))
                .containsExactly(INJECTION, XSS, MEMORY);
    }

    @Nested
    @DisplayName("아직 분류할 수 없으면 빈 목록이다")
    class NotYetClassified {

        @Test
        @DisplayName("약점이 적혀 있지 않은 CVE")
        void noWeakness() {
            assertThat(AttackTypes.classify(List.of())).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"NVD-CWE-noinfo", "NVD-CWE-Other", "CWE-", "not a cwe"})
        @DisplayName("NVD 가 정보 없음·기타로 적었거나 번호를 읽을 수 없는 약점만 있는 CVE")
        void noUsableWeakness(String cwe) {
            assertThat(AttackTypes.classify(List.of(cwe))).isEmpty();
        }

        @Test
        @DisplayName("정보 없음과 함께 읽을 수 있는 약점이 있으면 그 약점으로 나눈다")
        void usableWeaknessWins() {
            assertThat(AttackTypes.classify(List.of("NVD-CWE-noinfo", "CWE-79"))).containsExactly(XSS);
        }
    }

    @Test
    @DisplayName("다른 유형이 하나라도 붙으면 기타는 붙이지 않는다")
    void otherOnlyAlone() {
        assertThat(AttackTypes.classify(List.of("CWE-20", "CWE-79"))).containsExactly(XSS);
        assertThat(AttackTypes.classify(List.of("CWE-20", "CWE-1104"))).containsExactly(OTHER);
    }

    @Test
    @DisplayName("표에 없는 약점 번호는 기타다")
    void unlistedIsOther() {
        assertThat(AttackTypes.classify(List.of("CWE-1104"))).containsExactly(OTHER);
        assertThat(AttackTypes.isListed("CWE-1104")).isFalse();
    }

    @Test
    @DisplayName("소문자·앞뒤 공백이 섞인 약점 번호도 같은 번호로 읽는다")
    void lenientFormat() {
        assertThat(AttackTypes.classify(List.of(" cwe-79 "))).containsExactly(XSS);
    }

    @Test
    @DisplayName("최근 30일에 가장 많이 나온 약점 30개는 모두 표에 올라 있어 기타로 떨어지지 않는다(CWE-20·362 는 일부러 기타)")
    void topThirtyAreListed() {
        // 2026-11-01 실데이터 최근 30일, 많은 순
        List<String> top30 = Arrays.stream(new int[] {79, 862, 284, 416, 125, 89, 200, 863, 639, 122, 22, 306, 94,
                787, 119, 400, 918, 78, 502, 20, 287, 74, 121, 770, 269, 190, 476, 352, 362, 434})
                .mapToObj(number -> "CWE-" + number).toList();

        assertThat(top30).allMatch(AttackTypes::isListed);
        assertThat(top30.stream().filter(cwe -> AttackTypes.classify(List.of(cwe)).contains(OTHER)))
                .containsExactly("CWE-20", "CWE-362");
    }

    @Test
    @DisplayName("기타를 뺀 모든 유형에 약점이 하나 이상 있다")
    void everyTypeHasWeakness() {
        assertThat(Arrays.stream(AttackType.values()).filter(type -> type != OTHER))
                .allMatch(type -> AttackTypes.weaknessesOf(type).size() > 0);
    }
}
