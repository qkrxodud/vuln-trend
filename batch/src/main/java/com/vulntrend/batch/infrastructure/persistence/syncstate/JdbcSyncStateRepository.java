package com.vulntrend.batch.infrastructure.persistence.syncstate;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStateRepository;
import com.vulntrend.batch.domain.vulnerability.SyncStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcSyncStateRepository implements SyncStateRepository {

    private static final String FIND = """
            select source, last_synced_at, last_run_at, status
            from sync_state
            where source = :source
            """;

    private static final String UPSERT = """
            insert into sync_state (source, last_synced_at, last_run_at, status)
            values (:source, :lastSyncedAt, :lastRunAt, :status)
            on conflict (source) do update set
                last_synced_at = excluded.last_synced_at,
                last_run_at    = excluded.last_run_at,
                status         = excluded.status
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public SyncState findBySource(SourceName source) {
        List<SyncState> found = jdbc.query(FIND, new MapSqlParameterSource("source", source.getKey()), this::toState);
        return found.stream().findFirst().orElseGet(() -> SyncState.firstRun(source));
    }

    @Override
    public void save(SyncState state) {
        MapSqlParameterSource row = new MapSqlParameterSource()
                .addValue("source", state.getSource().getKey())
                .addValue("lastSyncedAt", toTimestamp(state.getLastSyncedAt()))
                .addValue("lastRunAt", toTimestamp(state.getLastRunAt()))
                .addValue("status", state.getStatus().name());
        jdbc.update(UPSERT, row);
    }

    private SyncState toState(ResultSet rs, int rowNum) throws SQLException {
        return SyncState.restore(
                SourceName.fromKey(rs.getString("source")),
                toInstant(rs.getObject("last_synced_at", OffsetDateTime.class)),
                toInstant(rs.getObject("last_run_at", OffsetDateTime.class)),
                SyncStatus.valueOf(rs.getString("status")));
    }

    // timestamptz 는 OffsetDateTime 으로 주고받는다(pgjdbc 는 Instant 를 직접 받지 않는다)
    private static OffsetDateTime toTimestamp(Optional<Instant> instant) {
        return instant.map(value -> value.atOffset(ZoneOffset.UTC)).orElse(null);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return Objects.isNull(value) ? null : value.toInstant();
    }
}
