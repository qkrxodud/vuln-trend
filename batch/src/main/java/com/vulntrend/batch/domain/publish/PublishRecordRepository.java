package com.vulntrend.batch.domain.publish;

import java.util.Optional;

/** 마지막 게시 기록 저장소(publish_state, 한 줄). 구현은 infrastructure/persistence/publish */
public interface PublishRecordRepository {

    /** 한 번도 게시하지 않았으면 비어 있다 */
    Optional<PublishRecord> findLast();

    /** 마지막 게시 기록을 이것으로 바꾼다 */
    void save(PublishRecord record);
}
