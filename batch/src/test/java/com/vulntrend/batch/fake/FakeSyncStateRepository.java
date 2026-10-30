package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStateRepository;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** 수집 진행 기록 저장소 흉내(메모리) */
public class FakeSyncStateRepository implements SyncStateRepository {

    private final Map<SourceName, SyncState> states = new EnumMap<>(SourceName.class);

    @Override
    public SyncState findBySource(SourceName source) {
        return Optional.ofNullable(states.get(source)).orElseGet(() -> SyncState.firstRun(source));
    }

    @Override
    public void save(SyncState state) {
        states.put(state.getSource(), state);
    }
}
