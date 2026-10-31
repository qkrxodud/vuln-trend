package com.vulntrend.batch.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("실행 방식")
class RunModeTest {

    @Test
    @DisplayName("따로 정한 것이 없으면 하루 한 번 실행이다")
    void defaultIsDaily() {
        RunMode mode = RunMode.from();

        assertThat(mode.isBackfill()).isFalse();
    }

    @Test
    @DisplayName("--backfill-days=365 를 주면 지난 365일을 한 번 가져오는 실행이다")
    void backfillDays() {
        RunMode mode = RunMode.from("--backfill-days=365");

        assertThat(mode.isBackfill()).isTrue();
        assertThat(mode.getBackfillSpan()).isEqualTo(Duration.ofDays(365));
    }

    @Test
    @DisplayName("스프링 설정 같은 다른 인자는 실행 방식에 영향을 주지 않는다")
    void otherArgumentsIgnored() {
        RunMode mode = RunMode.from("--spring.profiles.active=local", "--backfill-days=30", "extra");

        assertThat(mode.getBackfillSpan()).isEqualTo(Duration.ofDays(30));
        assertThat(RunMode.from("--spring.profiles.active=local").isBackfill()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badDays")
    @DisplayName("가져올 날 수가 1 이상의 정수가 아니면 시작하지 않는다")
    void refusesBadDays(String argument) {
        assertThatThrownBy(() -> RunMode.from(argument)).isInstanceOf(DomainValidationException.class);
    }

    static Stream<Named<String>> badDays() {
        return Stream.of(
                named("0일", "--backfill-days=0"),
                named("음수", "--backfill-days=-5"),
                named("숫자가 아닐 때", "--backfill-days=year"),
                named("값이 비어 있을 때", "--backfill-days="));
    }
}
