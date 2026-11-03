package com.vulntrend.batch.support;

import com.vulntrend.batch.application.gate.GateCheckService;
import com.vulntrend.batch.application.score.ScoreService;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStatus;
import com.vulntrend.batch.fake.FakeCveDetailRepository;
import com.vulntrend.batch.fake.FakeDocumentSizer;
import com.vulntrend.batch.fake.FakeEpssRepository;
import com.vulntrend.batch.fake.FakeGateQueryRepository;
import com.vulntrend.batch.fake.FakeRepositoryHistory;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import com.vulntrend.batch.fake.FakeSyncStateRepository;
import com.vulntrend.batch.fake.FakeTrendListRepository;
import com.vulntrend.batch.fake.FakeTrendScoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 러너 시험용 Gate G 배치 점검(백엔드 B 의 GateCheckServiceTest 와 같은 조립): 실패 항목이 없는 것과 있는 것 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GateChecks {

    /** 한국 시각 09:00 에 사흘 연속 데이터 커밋, 세 소스 모두 성공 — 실패 항목 없음 */
    public static GateCheckService passing(Clock clock, String... koreanRunTimes) {
        FakeRepositoryHistory history = new FakeRepositoryHistory().size(100L * 1024 * 1024, 0);
        for (String runAt : koreanRunTimes) {
            history.commit(runAt, 10);
        }
        FakeSyncStateRepository syncStates = new FakeSyncStateRepository();
        Instant ran = clock.instant();
        for (SourceName source : new SourceName[] {SourceName.NVD, SourceName.KEV, SourceName.EPSS}) {
            syncStates.save(SyncState.restore(source, ran, ran, SyncStatus.SUCCESS));
        }
        return service(history, syncStates, clock);
    }

    /** 데이터 커밋도 수집 기록도 없음 — 3일 연속 성공 항목이 실패 */
    public static GateCheckService failing(Clock clock) {
        return service(new FakeRepositoryHistory(), new FakeSyncStateRepository(), clock);
    }

    private static GateCheckService service(FakeRepositoryHistory history, FakeSyncStateRepository syncStates,
            Clock clock) {
        return new GateCheckService(history, syncStates, new FakeGateQueryRepository(),
                new FakeSiteDataWriter().sized("search-index.json", 1024L * 1024), new FakeDocumentSizer(),
                new FakeCveDetailRepository(), new FakeTrendListRepository(),
                new ScoreService(new FakeTrendScoreRepository(0), new FakeEpssRepository(Set.of()), clock), clock);
    }
}
