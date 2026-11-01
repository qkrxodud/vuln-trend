package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.publish.PublishRecord;
import com.vulntrend.batch.domain.publish.PublishRecordRepository;
import java.util.Optional;

/** 마지막 게시 기록 저장소 흉내(메모리, 한 줄) */
public class FakePublishRecordRepository implements PublishRecordRepository {

    private PublishRecord last;

    @Override
    public Optional<PublishRecord> findLast() {
        return Optional.ofNullable(last);
    }

    @Override
    public void save(PublishRecord record) {
        last = record;
    }
}
