package com.vulntrend.batch.domain.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("사이트 데이터 파일 경로")
class DataFilePathTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"meta.json", "search-index.json", "cve/CVE-2026-41203.json"})
    @DisplayName("데이터 폴더 아래 JSON 파일 경로를 받는다")
    void accepts(String value) {
        assertThat(DataFilePath.of(value).getValue()).isEqualTo(value);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"../index.json", "cve/../../x.json", "/etc/meta.json", "cve//x.json", "cve\\x.json",
            "meta.txt", ".json", "cve/.json", "./meta.json", "meta json.json"})
    @DisplayName("데이터 폴더 밖이나 JSON 이 아닌 경로는 받지 않는다")
    void refuses(String value) {
        assertThatThrownBy(() -> DataFilePath.of(value)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("비어 있는 경로는 받지 않는다")
    void refusesEmpty() {
        assertThatThrownBy(() -> DataFilePath.of("")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> DataFilePath.of(null)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("같은 경로는 같은 파일이다")
    void samePathSameFile() {
        assertThat(DataFilePath.of("cve/CVE-2026-41203.json")).isEqualTo(DataFilePath.of("cve/CVE-2026-41203.json"))
                .isNotEqualTo(DataFilePath.of("cve/CVE-2026-50001.json"));
    }
}
