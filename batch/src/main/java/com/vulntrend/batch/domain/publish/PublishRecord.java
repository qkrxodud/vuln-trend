package com.vulntrend.batch.domain.publish;

import com.vulntrend.batch.domain.stat.StatDays;
import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 마지막으로 원격까지 올린 게시(publish_state 한 줄). push 가 성공한 뒤에만 남긴다 — 실패한 게시는 다음 실행이 다시 올린다.
 * 날짜는 사이트 달력(한국 날짜)으로 둔다. 지문은 백엔드 B 의 보이는 지문(SHA-256 16진수 64자)이다.
 */
@Getter
public class PublishRecord {

    private static final int FINGERPRINT_MAX = 64;

    private final LocalDate publishedOn;
    private final String fingerprint;
    private final Instant publishedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PublishRecord(LocalDate publishedOn, String fingerprint, Instant publishedAt) {
        this.publishedOn = publishedOn;
        this.fingerprint = fingerprint;
        this.publishedAt = publishedAt;
    }

    /** 지금 올린 게시 */
    public static PublishRecord published(Instant at, String fingerprint) {
        return restore(LocalDate.ofInstant(at, StatDays.CALENDAR), fingerprint, at);
    }

    /** 저장소에서 읽은 값으로 되살린다 */
    public static PublishRecord restore(LocalDate publishedOn, String fingerprint, Instant publishedAt) {
        if (Objects.isNull(fingerprint) || fingerprint.isBlank() || fingerprint.length() > FINGERPRINT_MAX) {
            throw new DomainValidationException("A publish fingerprint must be 1 to 64 characters.");
        }
        return PublishRecord.builder().publishedOn(publishedOn).fingerprint(fingerprint).publishedAt(publishedAt)
                .build();
    }

    /** now 가 이 게시와 다른 한국 날짜인가 */
    public boolean isOtherDayThan(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, StatDays.CALENDAR);
        return today.isAfter(publishedOn) || today.isBefore(publishedOn);
    }

    public boolean hasFingerprint(String other) {
        return fingerprint.equals(other);
    }
}
