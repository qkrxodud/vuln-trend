package com.vulntrend.batch.domain.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("소스 하나의 수집 결과")
class CollectResultTest {

    @Test
    @DisplayName("다 가져온 소스는 몇 건을 적재했는지 남긴다")
    void loadedKeepsCount() {
        // when: NVD 에서 120건을 가져왔다
        CollectResult result = CollectResult.loaded(SourceName.NVD, 120);

        // then
        assertThat(result.getSource()).isEqualTo(SourceName.NVD);
        assertThat(result.getLoadedCount()).isEqualTo(120);
        assertThat(result.isSucceeded()).isTrue();
        assertThat(result.isFailed()).isFalse();
    }

    @Test
    @DisplayName("실패한 소스는 적재 건수 없이 실패로 남는다")
    void failedHasNoCount() {
        CollectResult result = CollectResult.failed(SourceName.KEV);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.isSucceeded()).isFalse();
        assertThat(result.getLoadedCount()).isZero();
    }

    @Test
    @DisplayName("적재 건수가 음수일 수는 없다")
    void negativeCountIsRefused() {
        assertThatThrownBy(() -> CollectResult.loaded(SourceName.NVD, -1))
                .isInstanceOf(DomainValidationException.class);
    }
}
