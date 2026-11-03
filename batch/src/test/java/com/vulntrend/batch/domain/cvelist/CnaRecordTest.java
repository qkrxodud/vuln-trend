package com.vulntrend.batch.domain.cvelist;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CVE.org 에서 받은 CNA 제품 목록")
class CnaRecordTest {

    private static final CveId FFMPEG_CVE = CveId.from("CVE-2026-120243");
    private static final CnaAffected FFMPEG = CnaAffected.of("FFmpeg", "FFmpeg", null, "unaffected",
            List.of(CnaVersion.of("2.0", "affected", null, "9.0.2", "custom")), List.of(), "pkg:github/ffmpeg/ffmpeg");

    @Test
    @DisplayName("CNA 가 제품을 하나라도 적었으면 채울 것이 있다")
    void hasAffected() {
        assertThat(CnaRecord.of(FFMPEG_CVE, List.of(FFMPEG)).hasAffected()).isTrue();
    }

    @Test
    @DisplayName("CNA 가 제품을 적지 않았으면 채울 것이 없다")
    void noAffected() {
        assertThat(CnaRecord.of(FFMPEG_CVE, List.of()).hasAffected()).isFalse();
    }

    @Test
    @DisplayName("받은 제품 목록이 저장된 것과 칸마다 같으면 같은 내용이다")
    void sameContent() {
        CnaAffected again = CnaAffected.of("FFmpeg", "FFmpeg", null, "unaffected",
                List.of(CnaVersion.of("2.0", "affected", null, "9.0.2", "custom")), List.of(),
                "pkg:github/ffmpeg/ffmpeg");

        assertThat(CnaRecord.of(FFMPEG_CVE, List.of(FFMPEG)).isSameAs(List.of(again))).isTrue();
    }

    @Test
    @DisplayName("버전 범위 한 칸만 달라도 다른 내용이다")
    void differentVersionRange() {
        CnaAffected widened = CnaAffected.of("FFmpeg", "FFmpeg", null, "unaffected",
                List.of(CnaVersion.of("2.0", "affected", null, "9.0.3", "custom")), List.of(),
                "pkg:github/ffmpeg/ffmpeg");

        assertThat(CnaRecord.of(FFMPEG_CVE, List.of(FFMPEG)).isSameAs(List.of(widened))).isFalse();
    }

    @Test
    @DisplayName("저장된 것이 없으면 제품을 받은 것과는 다른 내용이다")
    void nothingStoredYet() {
        assertThat(CnaRecord.of(FFMPEG_CVE, List.of(FFMPEG)).isSameAs(List.of())).isFalse();
    }

    @Test
    @DisplayName("CNA 가 적지 않은 칸은 비어 있는 것으로 읽힌다")
    void missingFieldsAreAbsent() {
        CnaAffected npmOnly = CnaAffected.of(null, null, "left-pad", null, List.of(), List.of(), null);
        CnaVersion bare = CnaVersion.of("1.0", "affected", null, null, null);

        assertThat(npmOnly.getVendor()).isEmpty();
        assertThat(npmOnly.getProduct()).isEmpty();
        assertThat(npmOnly.getPackageName()).contains("left-pad");
        assertThat(npmOnly.getDefaultStatus()).isEmpty();
        assertThat(npmOnly.getPackageUrl()).isEmpty();
        assertThat(bare.getLessThan()).isEmpty();
        assertThat(bare.getLessThanOrEqual()).isEmpty();
        assertThat(bare.getVersionType()).isEmpty();
    }
}
