package com.vulntrend.batch.application.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStatus;
import com.vulntrend.batch.fake.FakeCollector;
import com.vulntrend.batch.fake.FakeSyncStateRepository;
import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("수집 순회")
class CollectServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final Instant LAST_RUN = Instant.parse("2026-10-29T00:00:00Z");

    private final FakeSyncStateRepository syncStates = new FakeSyncStateRepository();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("각 수집기에 마지막으로 가져온 시각부터 지금까지의 구간을 넘긴다")
    void passesWindowFromCursor() {
        // given: 어제 끝까지 가져온 NVD
        syncStates.save(SyncState.restore(SourceName.NVD, LAST_RUN, LAST_RUN, SyncStatus.SUCCESS));
        FakeCollector nvd = FakeCollector.loading(SourceName.NVD, 5);

        // when
        new CollectService(List.of(nvd), syncStates, clock).collectAll();

        // then
        assertThat(nvd.receivedWindows()).hasSize(1);
        assertThat(nvd.receivedWindows().get(0).getFrom()).isEqualTo(LAST_RUN);
        assertThat(nvd.receivedWindows().get(0).getUntil()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("다 가져온 소스는 다음 실행이 지금부터 이어 가도록 진행 기록이 옮겨진다")
    void successAdvancesCursor() {
        CollectOutcome outcome = new CollectService(
                List.of(FakeCollector.loading(SourceName.NVD, 5)), syncStates, clock).collectAll();

        assertThat(outcome.hasFailure()).isFalse();
        assertThat(outcome.getResults().get(0).getLoadedCount()).isEqualTo(5);
        assertThat(syncStates.findBySource(SourceName.NVD).getLastSyncedAt()).contains(NOW);
        assertThat(syncStates.findBySource(SourceName.NVD).getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    @DisplayName("수집이 실패한 소스는 다음 실행에서 같은 구간을 다시 가져온다")
    void failureKeepsCursor() {
        syncStates.save(SyncState.restore(SourceName.KEV, LAST_RUN, LAST_RUN, SyncStatus.SUCCESS));

        CollectOutcome outcome = new CollectService(
                List.of(FakeCollector.failing(SourceName.KEV)), syncStates, clock).collectAll();

        assertThat(outcome.failedSources()).containsExactly(SourceName.KEV);
        assertThat(syncStates.findBySource(SourceName.KEV).getLastSyncedAt()).contains(LAST_RUN);
        assertThat(syncStates.findBySource(SourceName.KEV).getStatus()).isEqualTo(SyncStatus.FAIL);
        assertThat(syncStates.findBySource(SourceName.KEV).getLastRunAt()).contains(NOW);
    }

    @Test
    @DisplayName("한 소스가 실패해도 다른 소스는 끝까지 가져오고 진행 기록도 따로 옮겨진다")
    void sourcesAreIndependent() {
        // given: KEV 가 먼저 실패하고 그다음 EPSS
        FakeCollector kev = FakeCollector.failing(SourceName.KEV);
        FakeCollector epss = FakeCollector.loading(SourceName.EPSS, 300);

        // when
        CollectOutcome outcome = new CollectService(List.of(kev, epss), syncStates, clock).collectAll();

        // then
        assertThat(epss.receivedWindows()).hasSize(1);
        assertThat(outcome.failedSources()).containsExactly(SourceName.KEV);
        assertThat(syncStates.findBySource(SourceName.EPSS).getLastSyncedAt()).contains(NOW);
        assertThat(syncStates.findBySource(SourceName.KEV).getLastSyncedAt()).isEmpty();
    }

    @Test
    @DisplayName("수집기는 등록 순서와 상관없이 NVD → KEV → EPSS 순서로 돈다")
    void runsInSourceOrder() {
        // given: EPSS·KEV·NVD 순서로 등록됨 — KEV 표시가 같은 실행에서 NVD 가 새로 넣은 CVE 에도 붙으려면 NVD 가 먼저다
        List<FakeCollector> registered = List.of(FakeCollector.loading(SourceName.EPSS, 1),
                FakeCollector.loading(SourceName.KEV, 1), FakeCollector.loading(SourceName.NVD, 1));

        // when
        CollectOutcome outcome = new CollectService(List.copyOf(registered), syncStates, clock).collectAll();

        // then
        assertThat(outcome.getResults()).extracting(result -> result.getSource())
                .containsExactly(SourceName.NVD, SourceName.KEV, SourceName.EPSS);
    }

    @Nested
    @DisplayName("지난 기간을 한 번 가져올 때(백필)")
    class Backfill {

        @Test
        @DisplayName("지금부터 정한 날 수만큼 거슬러 올라간 구간을 그 소스의 수집기에만 넘긴다")
        void passesSpanToOneSource() {
            FakeCollector nvd = FakeCollector.loading(SourceName.NVD, 900);
            FakeCollector kev = FakeCollector.loading(SourceName.KEV, 1);

            CollectResult result = new CollectService(List.of(kev, nvd), syncStates, clock)
                    .backfill(SourceName.NVD, Duration.ofDays(365));

            assertThat(result.getLoadedCount()).isEqualTo(900);
            assertThat(nvd.receivedWindows()).singleElement().satisfies(window -> {
                assertThat(window.getFrom()).isEqualTo(NOW.minus(Duration.ofDays(365)));
                assertThat(window.getUntil()).isEqualTo(NOW);
            });
            assertThat(kev.receivedWindows()).isEmpty();
        }

        @Test
        @DisplayName("다 가져오면 다음 하루 한 번 실행이 지금부터 이어 가도록 진행 기록이 옮겨진다")
        void advancesCursor() {
            new CollectService(List.of(FakeCollector.loading(SourceName.NVD, 1)), syncStates, clock)
                    .backfill(SourceName.NVD, Duration.ofDays(365));

            assertThat(syncStates.findBySource(SourceName.NVD).getLastSyncedAt()).contains(NOW);
        }

        @Test
        @DisplayName("실패하면 실패로 기록되고 마지막으로 가져온 시각은 그대로 남는다")
        void failureKeepsCursor() {
            syncStates.save(SyncState.restore(SourceName.NVD, LAST_RUN, LAST_RUN, SyncStatus.SUCCESS));

            CollectResult result = new CollectService(List.of(FakeCollector.failing(SourceName.NVD)), syncStates, clock)
                    .backfill(SourceName.NVD, Duration.ofDays(365));

            assertThat(result.isFailed()).isTrue();
            assertThat(syncStates.findBySource(SourceName.NVD).getStatus()).isEqualTo(SyncStatus.FAIL);
            assertThat(syncStates.findBySource(SourceName.NVD).getLastSyncedAt()).contains(LAST_RUN);
        }

        @Test
        @DisplayName("그 소스의 수집기가 없으면 시작하지 않는다")
        void missingCollectorIsRefused() {
            CollectService service = new CollectService(List.of(FakeCollector.loading(SourceName.KEV, 1)), syncStates,
                    clock);

            assertThatThrownBy(() -> service.backfill(SourceName.NVD, Duration.ofDays(365)))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    @Test
    @DisplayName("수집기가 하나도 없으면 실패 없이 빈 결과가 나온다")
    void noCollectors() {
        CollectOutcome outcome = new CollectService(List.of(), syncStates, clock).collectAll();

        assertThat(outcome.getResults()).isEmpty();
        assertThat(outcome.hasFailure()).isFalse();
    }
}
