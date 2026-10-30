package com.vulntrend.batch.domain.collect;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 수집 소스. key 는 sync_state.source·vulnerability_raw.source 에 적는 소문자 이름 */
@Getter
@RequiredArgsConstructor
public enum SourceName {

    NVD("nvd"),
    KEV("kev"),
    EPSS("epss"),
    OSV("osv"),
    NEWS("news");

    private final String key;

    public static SourceName fromKey(String key) {
        return Arrays.stream(values())
                .filter(source -> source.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new DomainValidationException("Unknown source name: " + key));
    }
}
