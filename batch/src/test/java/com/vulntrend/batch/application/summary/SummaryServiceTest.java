package com.vulntrend.batch.application.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.summary.KoreanSummary;
import com.vulntrend.batch.domain.summary.SummaryOutcome;
import com.vulntrend.batch.domain.summary.SummaryRepository;
import com.vulntrend.batch.domain.summary.SummaryResult;
import com.vulntrend.batch.domain.summary.SummarySource;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.fake.FakeSummarizer;
import com.vulntrend.batch.fake.FakeSummaryRepository;
import com.vulntrend.batch.global.config.SummaryProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisplayName("한국어 요약")
@ExtendWith(OutputCaptureExtension.class)
class SummaryServiceTest {

    /** 한국 시각 2026-10-31 10:00 실행 */
    private static final Instant NOW = Instant.parse("2026-10-31T01:00:00Z");
    private static final Instant KOREAN_MIDNIGHT = Instant.parse("2026-10-30T15:00:00Z");
    private static final String KEY = "summary-key-41c9e7";
    private static final String MODEL = "gpt-6-luna";
    private static final SummaryProperties CONFIGURED = new SummaryProperties(20, 40, MODEL, KEY);
    private static final KoreanSummary GOOD = KoreanSummary.of("원격 코드 실행 취약점이다. 쉽게 악용된다.",
            "Foo 사용자가 영향받는다.", "원문 참고");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Nested
    @DisplayName("요약 열쇠가 없으면")
    class NotConfigured {

        @Test
        @DisplayName("요약을 하나도 만들지 않고 이번 실행은 요약 없이 넘어간다")
        void skipsWithoutKey(CapturedOutput output) {
            FakeSummaryRepository repository = new FakeSummaryRepository(targets(3), 0);

            int generated = new SummaryService(new SummaryProperties(20, 40, MODEL, ""), Optional.empty(), repository,
                    clock).summarizeTop();

            assertThat(generated).isZero();
            assertThat(repository.calls()).isEmpty();
            assertThat(output).contains("Korean summary is skipped");
        }
    }

    @Nested
    @DisplayName("요약 열쇠가 있으면")
    class Configured {

        @Test
        @DisplayName("트렌드 상위 20건 중 요약이 없는 CVE 를 하나씩 요약한다")
        void summarizesTopTwenty() {
            FakeSummaryRepository repository = new FakeSummaryRepository(targets(3), 0);
            FakeSummarizer summarizer = generating(MODEL);

            int generated = service(summarizer, repository).summarizeTop();

            assertThat(generated).isEqualTo(3);
            assertThat(repository.requestedTop()).isEqualTo(20);
            assertThat(summarizer.asked()).hasSize(3);
            assertThat(repository.saved()).hasSize(3);
        }

        @Test
        @DisplayName("요약에는 실제로 답한 모델 이름을 남긴다")
        void recordsAnsweringModel() {
            FakeSummaryRepository repository = new FakeSummaryRepository(targets(1), 0);

            service(generating("gpt-6-luna-2026-05-18"), repository).summarizeTop();

            assertThat(repository.saved().get(id(1)).model()).isEqualTo("gpt-6-luna-2026-05-18");
        }

        @Test
        @DisplayName("무엇이 문제인가 문장의 첫 문장이 설명에서 만든 임시 제목을 대신한다")
        void titleFromWhat() {
            FakeSummaryRepository repository = new FakeSummaryRepository(targets(1), 0);

            service(generating(MODEL), repository).summarizeTop();

            FakeSummaryRepository.Saved saved = repository.saved().get(id(1));
            assertThat(saved.summaryTitle()).isEqualTo("원격 코드 실행 취약점이다");
            assertThat(saved.descriptionTitle()).isEqualTo("Foo 1 allows remote code execution");
        }

        @Test
        @DisplayName("버리거나 거절되거나 실패한 요약은 남기지 않고 호출만 기록한다")
        void recordsEveryCall() {
            List<SummarySource> sources = targets(3);
            FakeSummaryRepository repository = new FakeSummaryRepository(sources, 0);
            FakeSummarizer summarizer = new FakeSummarizer(source -> switch (source.getCveId().getValue()) {
                case "CVE-2026-0001" -> SummaryResult.from(KoreanSummary.of("문제다.", "누구", "sudo rm -rf / 실행"),
                        MODEL, source);
                case "CVE-2026-0002" -> SummaryResult.refused("gpt-6-luna-2026-05-18");
                default -> SummaryResult.failed();
            });

            int generated = service(summarizer, repository).summarizeTop();

            assertThat(generated).isZero();
            assertThat(repository.saved()).isEmpty();
            assertThat(repository.calls()).containsEntry(id(1), SummaryOutcome.DISCARDED)
                    .containsEntry(id(2), SummaryOutcome.REFUSED)
                    .containsEntry(id(3), SummaryOutcome.FAILED);
        }

        @Test
        @DisplayName("요약을 버리면 CVE·걸린 규칙·걸린 값을 실행 기록 한 줄로 남기고 요약 문장은 남기지 않는다")
        void logsDiscardReason(CapturedOutput output) {
            FakeSummarizer summarizer = new FakeSummarizer(source -> SummaryResult.from(
                    KoreanSummary.of("남기면 안 되는 첫 문장이다.", "Foo 9.9.9 사용자가 영향받는다.", "원문 참고"), MODEL, source));

            service(summarizer, new FakeSummaryRepository(targets(1), 0)).summarizeTop();

            assertThat(output).contains("Korean summary for CVE-2026-0001 was discarded (UNKNOWN_VERSION: \"9.9.9\").");
            assertThat(output).doesNotContain("남기면 안 되는 첫 문장");
        }

        @Test
        @DisplayName("결과를 건수로 실행 기록에 남기고 요약 열쇠 값은 남기지 않는다")
        void logsCounts(CapturedOutput output) {
            service(generating(MODEL), new FakeSummaryRepository(targets(2), 0)).summarizeTop();

            assertThat(output).contains("2 generated, 0 discarded, 0 refused, 0 failed");
            assertThat(output).doesNotContain(KEY);
        }
    }

    @Nested
    @DisplayName("하루 상한 — 답을 받은 호출 20콜")
    class DailyLimit {

        @Test
        @DisplayName("오늘 호출 수는 한국 시각 자정부터 센다")
        void countsFromKoreanMidnight() {
            FakeSummaryRepository repository = new FakeSummaryRepository(targets(1), 0);

            service(generating(MODEL), repository).summarizeTop();

            assertThat(repository.answeredSince()).isEqualTo(KOREAN_MIDNIGHT);
            assertThat(repository.failedSince()).isEqualTo(KOREAN_MIDNIGHT);
        }

        @Test
        @DisplayName("오늘 이미 18번 답을 받았으면 남은 2번만 부른다")
        void callsOnlyWhatIsLeft(CapturedOutput output) {
            FakeSummarizer summarizer = generating(MODEL);

            service(summarizer, new FakeSummaryRepository(targets(5), 18)).summarizeTop();

            assertThat(summarizer.asked()).extracting(SummarySource::getCveId).containsExactly(id(1), id(2));
            assertThat(output).contains("3 wait for tomorrow");
        }

        @Test
        @DisplayName("오늘 20번 답을 받았으면 더 부르지 않고 이번 실행은 요약 없이 넘어간다")
        void limitUsedUp(CapturedOutput output) {
            FakeSummarizer summarizer = generating(MODEL);

            int generated = service(summarizer, new FakeSummaryRepository(targets(3), 20)).summarizeTop();

            assertThat(generated).isZero();
            assertThat(summarizer.asked()).isEmpty();
            assertThat(output).contains("daily limit of 20 answered calls is used up");
        }

        @Test
        @DisplayName("오늘 실패한 호출이 20번 있어도 새 요약 20건을 만든다")
        void failuresDoNotUseTheLimit() {
            FakeSummarizer summarizer = generating(MODEL);

            int generated = service(summarizer, new FakeSummaryRepository(targets(20), 0, 20)).summarizeTop();

            assertThat(generated).isEqualTo(20);
            assertThat(summarizer.asked()).hasSize(20);
        }

        @Test
        @DisplayName("실행 도중 실패한 호출은 상한을 쓰지 않아 남은 대상을 이어서 부른다")
        void failuresDuringRunDoNotUseTheLimit() {
            FakeSummarizer summarizer = new FakeSummarizer(source -> source.getCveId().getValue()
                    .compareTo("CVE-2026-0003") <= 0 ? SummaryResult.failed() : SummaryResult.from(GOOD, MODEL, source));

            int generated = service(summarizer, new FakeSummaryRepository(targets(5), 18)).summarizeTop();

            assertThat(summarizer.asked()).hasSize(5);
            assertThat(generated).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("실패 호출 안전 상한 40콜")
    class FailureLimit {

        @Test
        @DisplayName("오늘 실패가 40번 쌓였으면 더 부르지 않고 열쇠·모델을 확인하라고 경고한다")
        void failureLimitReached(CapturedOutput output) {
            FakeSummarizer summarizer = generating(MODEL);

            int generated = service(summarizer, new FakeSummaryRepository(targets(3), 0, 40)).summarizeTop();

            assertThat(generated).isZero();
            assertThat(summarizer.asked()).isEmpty();
            assertThat(output).contains("failure limit of 40 calls is reached");
            assertThat(output).doesNotContain(KEY);
        }

        @Test
        @DisplayName("실행 도중 실패가 40번에 닿으면 그 자리에서 멈춘다")
        void stopsWhenFailuresReachTheLimit(CapturedOutput output) {
            FakeSummarizer summarizer = new FakeSummarizer(source -> SummaryResult.failed());

            service(summarizer, new FakeSummaryRepository(targets(5), 0, 38)).summarizeTop();

            assertThat(summarizer.asked()).extracting(SummarySource::getCveId).containsExactly(id(1), id(2));
            assertThat(output).contains("failure limit of 40 calls is reached");
            assertThat(output).contains("3 wait for tomorrow");
        }
    }

    @Test
    @DisplayName("요약 단계가 실패해도 멈추지 않고 이번 실행은 요약 없이 다음 단계로 간다")
    void failureIsNotFatal(CapturedOutput output) {
        SummaryRepository broken = new FakeSummaryRepository(targets(1), 0) {
            @Override
            public int countAnsweredSince(Instant since) {
                throw new IllegalStateException("store is down");
            }
        };

        int generated = new SummaryService(CONFIGURED, Optional.of(generating(MODEL)), broken, clock).summarizeTop();

        assertThat(generated).isZero();
        assertThat(output).contains("Korean summary step failed (IllegalStateException)");
        assertThat(output).doesNotContain("store is down");
    }

    private SummaryService service(FakeSummarizer summarizer, FakeSummaryRepository repository) {
        return new SummaryService(CONFIGURED, Optional.of(summarizer), repository, clock);
    }

    private static FakeSummarizer generating(String model) {
        return new FakeSummarizer(source -> SummaryResult.from(GOOD, model, source));
    }

    private static List<SummarySource> targets(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> SummarySource.of(id(i), "Foo " + i + " allows remote code execution. Details follow.",
                        List.of(), null))
                .toList();
    }

    private static CveId id(int i) {
        return CveId.from("CVE-2026-%04d".formatted(i));
    }
}
