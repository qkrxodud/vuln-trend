package com.vulntrend.batch.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("오늘 남은 요약 호출")
class SummaryCallBudgetTest {

    @Nested
    @DisplayName("하루 상한은 답을 받은 호출만 센다")
    class Answered {

        @Test
        @DisplayName("만들고 버리고 거절된 호출이 상한에 닿으면 다 쓴 것이다")
        void answeredCallsUseTheLimit() {
            SummaryCallBudget budget = SummaryCallBudget.of(3, 40, 0, 0);

            budget.spend(SummaryOutcome.GENERATED);
            budget.spend(SummaryOutcome.DISCARDED);
            budget.spend(SummaryOutcome.REFUSED);

            assertThat(budget.isAnsweredUsedUp()).isTrue();
            assertThat(budget.isUsedUp()).isTrue();
            assertThat(budget.isFailureLimitReached()).isFalse();
        }

        @Test
        @DisplayName("실패한 호출은 하루 상한을 쓰지 않는다")
        void failuresDoNotUseTheLimit() {
            SummaryCallBudget budget = SummaryCallBudget.of(20, 40, 19, 20);

            budget.spend(SummaryOutcome.FAILED);

            assertThat(budget.isAnsweredUsedUp()).isFalse();
            assertThat(budget.isUsedUp()).isFalse();
        }
    }

    @Nested
    @DisplayName("실패 안전 상한은 실패한 호출만 센다")
    class Failures {

        @Test
        @DisplayName("오늘 실패가 안전 상한에 닿으면 하루 상한이 남아도 다 쓴 것이다")
        void failureLimitStopsTheDay() {
            SummaryCallBudget budget = SummaryCallBudget.of(20, 40, 0, 39);

            budget.spend(SummaryOutcome.FAILED);

            assertThat(budget.isFailureLimitReached()).isTrue();
            assertThat(budget.isUsedUp()).isTrue();
            assertThat(budget.isAnsweredUsedUp()).isFalse();
        }

        @Test
        @DisplayName("답을 받은 호출은 실패 안전 상한을 쓰지 않는다")
        void answeredCallsDoNotUseTheFailureLimit() {
            SummaryCallBudget budget = SummaryCallBudget.of(20, 1, 0, 0);

            budget.spend(SummaryOutcome.GENERATED);

            assertThat(budget.isFailureLimitReached()).isFalse();
        }
    }
}
