package com.vulntrend.batch.domain.score;

import com.vulntrend.batch.domain.stat.StatDays;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 시간 감쇠를 세는 기준 시각(S4, 오케스트레이터 결정 2026-11-02 B안). 실행 시각으로 세면 2시간마다 감쇠가 바뀌어
 * 상세 파일 수백 개가 다시 쓰인다. 그래서 하루에 한 번만 바뀌는 시각을 쓴다.
 * - 기준: 마지막으로 반영한 EPSS 점수 파일 날짜의 세계 표준시 자정. 감쇠와 EPSS 가 같은 실행에서 바뀌어 전체 재작성이 하루 한 번이다
 * - 대체: EPSS 가 이틀 넘게 끊겼거나 반영한 적이 없으면 한국 오늘 자정. 대체했는지는 isFallback() 으로 알린다
 * 점수 다시 매기기·목록·상세·보이는 지문이 모두 이 시각을 써서 같은 날 실행끼리는 같은 점수가 나온다.
 */
@Getter
public final class DecayBase {

    private static final int STALE_AFTER_DAYS = 2;

    private final Instant at;
    private final boolean fallback;

    @Builder(access = AccessLevel.PRIVATE)
    private DecayBase(Instant at, boolean fallback) {
        this.at = at;
        this.fallback = fallback;
    }

    public static DecayBase of(Optional<LocalDate> epssScoreDate, Instant now) {
        LocalDate koreanToday = LocalDate.ofInstant(now, StatDays.CALENDAR);
        return epssScoreDate.filter(date -> date.isAfter(koreanToday.minusDays(STALE_AFTER_DAYS + 1)))
                .map(date -> DecayBase.builder().at(date.atStartOfDay(ZoneOffset.UTC).toInstant()).fallback(false).build())
                .orElseGet(() -> DecayBase.builder()
                        .at(koreanToday.atStartOfDay(StatDays.CALENDAR).toInstant()).fallback(true).build());
    }
}
