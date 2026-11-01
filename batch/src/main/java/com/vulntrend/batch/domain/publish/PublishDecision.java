package com.vulntrend.batch.domain.publish;

import java.time.Instant;
import java.util.Optional;

/**
 * 이번 실행에서 게시할지(2시간마다 실행, 화면이 바뀔 때만 게시 — 사용자 승인 2026-11-01).
 * - FIRST_OF_DAY: 그 한국 날짜에 아직 게시하지 않았다. 날짜가 바뀌면 기한(D-n)·"오늘"·감쇠가 달라지므로 하루 최소 한 번 올리고,
 *   이때만 검색 색인을 다시 만든다
 * - VIEW_CHANGED: 오늘 이미 올렸지만 보이는 지문이 마지막 게시와 다르다
 * - NO_CHANGE: 오늘 이미 올렸고 보이는 지문도 같다 — 데이터 파일을 쓰지도 올리지도 않는다
 */
public enum PublishDecision {
    FIRST_OF_DAY, VIEW_CHANGED, NO_CHANGE;

    public static PublishDecision decide(Optional<PublishRecord> last, Instant now, String fingerprint) {
        return last.map(record -> decideAfter(record, now, fingerprint)).orElse(FIRST_OF_DAY);
    }

    private static PublishDecision decideAfter(PublishRecord last, Instant now, String fingerprint) {
        if (last.isOtherDayThan(now)) {
            return FIRST_OF_DAY;
        }
        if (last.hasFingerprint(fingerprint)) {
            return NO_CHANGE;
        }
        return VIEW_CHANGED;
    }

    public boolean isSkipped() {
        return this == NO_CHANGE;
    }

    public boolean rebuildsSearchIndex() {
        return this == FIRST_OF_DAY;
    }
}
