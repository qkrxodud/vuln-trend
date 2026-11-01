package com.vulntrend.batch.infrastructure.epss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.domain.enrichment.EpssScore;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EPSS 점수 파일 읽기")
class EpssReadingTest {

    private static final Set<CveId> KNOWN = Set.of(CveId.from("CVE-2021-44228"), CveId.from("CVE-2024-3400"));
    private static final List<String> FILE = List.of(
            "#model_version:v2026.06.15,score_date:2026-10-30T12:03:08Z",
            "cve,epss,percentile",
            "CVE-1999-0001,0.04753,0.91871",
            "CVE-2021-44228,0.99999,1.0",
            "CVE-2024-3400,0.94310,0.99912");

    @Nested
    @DisplayName("FIRST 가 준 파일을 줄마다 읽으면")
    class Reading {

        @Test
        @DisplayName("취약점 목록에 있는 CVE 의 확률과 순위만 남긴다")
        void keepsOnlyKnownCves() {
            List<EpssScore> scores = read(KNOWN, FILE);

            assertThat(scores).extracting(score -> score.getCveId().getValue())
                    .containsExactly("CVE-2021-44228", "CVE-2024-3400");
            assertThat(scores.get(1).getScore()).isEqualByComparingTo("0.94310");
            assertThat(scores.get(1).getPercentile()).isEqualByComparingTo("0.99912");
        }

        @Test
        @DisplayName("파일 전체의 점수 줄 수를 센다")
        void countsAllRows() {
            EpssReading reading = EpssReading.keeping(KNOWN);
            FILE.forEach(reading::accept);
            reading.finish();

            assertThat(reading.getRows()).isEqualTo(3);
        }

        @Test
        @DisplayName("모양이 틀린 줄은 건너뛰고 나머지를 읽는다")
        void skipsBrokenRows() {
            List<String> broken = List.of("cve,epss,percentile",
                    "CVE-2021-44228,not-a-number,1.0",
                    "CVE-2024-3400,0.94310",
                    "CVE-2024-3400,1.5,0.9",
                    "",
                    "CVE-2024-3400,0.94310,0.99912");

            assertThat(read(KNOWN, broken)).extracting(score -> score.getCveId().getValue())
                    .containsExactly("CVE-2024-3400");
        }

        @Test
        @DisplayName("취약점 목록이 비어 있으면 남기는 것 없이 읽기를 마친다")
        void emptyListKeepsNothing() {
            assertThat(read(Set.of(), FILE)).isEmpty();
        }
    }

    @Nested
    @DisplayName("파일이 잘못 왔으면")
    class BrokenFile {

        @Test
        @DisplayName("머리글이 cve,epss,percentile 이 아니면 EPSS 수집이 실패한다")
        void wrongHeaderFails() {
            EpssReading reading = EpssReading.keeping(KNOWN);

            assertThatThrownBy(() -> reading.accept("cve,score"))
                    .isInstanceOf(SourceFetchException.class)
                    .hasMessageContaining("epss");
        }

        @Test
        @DisplayName("머리글 없이 끝나면 EPSS 수집이 실패한다")
        void noHeaderFails() {
            EpssReading reading = EpssReading.keeping(KNOWN);
            reading.accept("#model_version:v2026.06.15");

            assertThatThrownBy(reading::finish).isInstanceOf(SourceFetchException.class);
        }

        @Test
        @DisplayName("점수 줄이 하나도 없으면 EPSS 수집이 실패한다")
        void noRowsFails() {
            EpssReading reading = EpssReading.keeping(KNOWN);
            reading.accept("cve,epss,percentile");

            assertThatThrownBy(reading::finish).isInstanceOf(SourceFetchException.class);
        }
    }

    private static List<EpssScore> read(Set<CveId> known, List<String> lines) {
        EpssReading reading = EpssReading.keeping(known);
        lines.forEach(reading::accept);
        return reading.finish();
    }

    @Test
    @DisplayName("첫 줄 주석에서 점수 날짜를 읽고, 적혀 있지 않으면 모른다")
    void scoreDate() {
        EpssReading dated = EpssReading.keeping(KNOWN);
        FILE.forEach(dated::accept);
        EpssReading undated = EpssReading.keeping(KNOWN);
        FILE.subList(1, FILE.size()).forEach(undated::accept);

        assertThat(dated.scoreDate()).contains(LocalDate.parse("2026-10-30"));
        assertThat(undated.scoreDate()).isEmpty();
    }
}
