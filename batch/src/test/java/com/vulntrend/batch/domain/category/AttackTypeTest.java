package com.vulntrend.batch.domain.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("공격 유형")
class AttackTypeTest {

    @Test
    @DisplayName("사이트가 쓰는 키는 열 개이고 정해진 순서다")
    void keysInOrder() {
        assertThat(Arrays.stream(AttackType.values()).map(AttackType::getKey)).containsExactly(
                "rce", "injection", "xss", "auth", "info", "memory", "dos", "path", "request_forgery", "other");
    }

    @Test
    @DisplayName("유형마다 한국어 이름이 있고 겹치지 않는다")
    void koreanNames() {
        assertThat(Arrays.stream(AttackType.values()).map(AttackType::getKoreanName))
                .doesNotContainNull().doesNotHaveDuplicates();
        assertThat(AttackType.RCE.getKoreanName()).isEqualTo("원격 코드 실행");
    }
}
