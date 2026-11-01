package com.vulntrend.batch.application.publish;

import com.vulntrend.batch.domain.publish.PublishDecision;
import com.vulntrend.batch.domain.publish.PublishRecord;
import com.vulntrend.batch.domain.publish.PublishRecordRepository;
import com.vulntrend.batch.domain.publish.PublishResult;
import com.vulntrend.batch.domain.publish.Publisher;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 게시 판정과 게시(2시간마다 실행, 화면이 바뀔 때만 게시 — 사용자 승인 2026-11-01).
 * 러너가 요약 단계 다음에 보이는 지문으로 decide 를 부르고, 게시하기로 했으면 데이터 파일을 쓴 뒤 publish 를 부른다.
 * 마지막 게시 기록은 push 가 성공한 뒤에만 바꾼다 — 실패하면 기록이 그대로라 다음 실행이 다시 올린다.
 */
@Service
@RequiredArgsConstructor
public class SitePublishService {

    private final Publisher publisher;
    private final PublishRecordRepository records;
    private final Clock clock;

    public PublishDecision decide(String fingerprint) {
        return PublishDecision.decide(records.findLast(), clock.instant(), fingerprint);
    }

    /** fingerprint 는 실제로 쓴 데이터 파일의 지문이다 */
    public PublishResult publish(String fingerprint) {
        PublishResult result = publisher.publish();
        records.save(PublishRecord.published(clock.instant(), fingerprint));
        return result;
    }
}
