package com.vulntrend.batch.domain.export;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 사이트 데이터 파일의 상대 경로(output.dir 아래, WBS 4.1). 예: meta.json, cve/CVE-2026-41203.json.
 * 이름 조각은 영숫자로 시작하고 영숫자·밑줄·하이픈만 쓴다 — 데이터 폴더 밖(.., 절대 경로)이나 JSON 이 아닌 파일은 만들 수 없다.
 */
@Getter
public class DataFilePath {

    private static final Pattern SHAPE =
            Pattern.compile("([A-Za-z0-9][A-Za-z0-9_-]*/)*[A-Za-z0-9][A-Za-z0-9_-]*\\.json");

    private final String value;

    @Builder(access = AccessLevel.PRIVATE)
    private DataFilePath(String value) {
        this.value = value;
    }

    public static DataFilePath of(String value) {
        boolean valid = Objects.nonNull(value) && SHAPE.matcher(value).matches();
        if (valid) {
            return DataFilePath.builder().value(value).build();
        }
        throw new DomainValidationException("Data file path must be a JSON file inside the data folder.");
    }
}
