package com.vulntrend.batch.domain.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("소스")
class SourceNameTest {

    @Test
    @DisplayName("수집 기록과 원본 보관에는 소스를 소문자 이름으로 적는다")
    void keyIsLowercase() {
        // given / when / then: NVD 는 nvd, 뉴스는 news 로 적힌다
        assertThat(SourceName.NVD.getKey()).isEqualTo("nvd");
        assertThat(SourceName.NEWS.getKey()).isEqualTo("news");
    }

    @Test
    @DisplayName("적어 둔 소문자 이름으로 소스를 다시 알아본다")
    void fromKeyFindsSource() {
        assertThat(SourceName.fromKey("epss")).isEqualTo(SourceName.EPSS);
    }

    @Test
    @DisplayName("모르는 소스 이름은 받지 않는다")
    void unknownKeyIsRefused() {
        assertThatThrownBy(() -> SourceName.fromKey("twitter"))
                .isInstanceOf(DomainValidationException.class);
    }
}
