package com.vulntrend.batch.infrastructure.persistence.publish;

import com.vulntrend.batch.domain.publish.PublishRecord;
import com.vulntrend.batch.domain.publish.PublishRecordRepository;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcPublishRecordRepository implements PublishRecordRepository {

    private static final String FIND = """
            select published_on, fingerprint, published_at
            from publish_state
            where id = 1
            """;

    private static final String UPSERT = """
            insert into publish_state (id, published_on, fingerprint, published_at)
            values (1, :publishedOn, :fingerprint, :publishedAt)
            on conflict (id) do update set
                published_on = excluded.published_on,
                fingerprint  = excluded.fingerprint,
                published_at = excluded.published_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Optional<PublishRecord> findLast() {
        return jdbc.query(FIND, Map.of(), this::toRecord).stream().findFirst();
    }

    @Override
    public void save(PublishRecord record) {
        MapSqlParameterSource row = new MapSqlParameterSource()
                .addValue("publishedOn", Date.valueOf(record.getPublishedOn()))
                .addValue("fingerprint", record.getFingerprint())
                .addValue("publishedAt", record.getPublishedAt().atOffset(ZoneOffset.UTC));
        jdbc.update(UPSERT, row);
    }

    private PublishRecord toRecord(ResultSet rs, int rowNum) throws SQLException {
        return PublishRecord.restore(rs.getDate("published_on").toLocalDate(), rs.getString("fingerprint"),
                rs.getObject("published_at", OffsetDateTime.class).toInstant());
    }
}
