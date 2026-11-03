package com.vulntrend.batch.domain.cvelist;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * CNA affected 의 버전 한 줄(CVE JSON 5 versions[]). 원문 그대로 담고 해석하지 않는다 — 수정 버전·범위 해석은 3b(백엔드 B).
 * version·status 는 CVE JSON 5 에서 필수, 나머지는 없을 수 있다.
 */
@EqualsAndHashCode
public class CnaVersion {

    @Getter
    private final String version;
    @Getter
    private final String status;
    private final String lessThan;
    private final String lessThanOrEqual;
    private final String versionType;

    @Builder(access = AccessLevel.PRIVATE)
    private CnaVersion(String version, String status, String lessThan, String lessThanOrEqual, String versionType) {
        this.version = version;
        this.status = status;
        this.lessThan = lessThan;
        this.lessThanOrEqual = lessThanOrEqual;
        this.versionType = versionType;
    }

    /** 없는 칸은 null 로 받는다 */
    public static CnaVersion of(String version, String status, String lessThan, String lessThanOrEqual,
            String versionType) {
        return CnaVersion.builder().version(version).status(status).lessThan(lessThan)
                .lessThanOrEqual(lessThanOrEqual).versionType(versionType).build();
    }

    public Optional<String> getLessThan() {
        return Optional.ofNullable(lessThan);
    }

    public Optional<String> getLessThanOrEqual() {
        return Optional.ofNullable(lessThanOrEqual);
    }

    public Optional<String> getVersionType() {
        return Optional.ofNullable(versionType);
    }
}
