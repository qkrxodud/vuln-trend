package com.vulntrend.batch.infrastructure.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.WriteOutcome;
import com.vulntrend.batch.global.config.OutputProperties;
import com.vulntrend.batch.global.error.exception.OutputWriteException;
import com.vulntrend.batch.infrastructure.export.dto.ExportSampleJson;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("사이트 데이터 파일 쓰기")
class JsonFileWriterTest {

    private static final DataFilePath META = DataFilePath.of("meta.json");
    private static final FileTime LONG_AGO = FileTime.from(Instant.parse("2026-01-01T00:00:00Z"));

    @TempDir
    private Path dataDir;

    private JsonFileWriter writer;

    @BeforeEach
    void writer() {
        writer = new JsonFileWriter(new OutputProperties(dataDir.toString()));
    }

    @Nested
    @DisplayName("JSON 모양은")
    class Shape {

        @Test
        @DisplayName("이름은 밑줄 표기, 들여쓰기 두 칸, 끝은 줄바꿈 하나다")
        void snakeCaseTwoSpaces() throws IOException {
            writer.write(META, sample("CVE-2026-41203", null));

            assertThat(read("meta.json")).isEqualTo("""
                    {
                      "id": "CVE-2026-41203",
                      "score": 67,
                      "epss": 0.94310,
                      "epss_percentile": 0.99900,
                      "kev_due_date": "2026-11-03",
                      "updated_at": "2026-10-31T09:00:00+09:00",
                      "primary_product": null,
                      "is_kev": true,
                      "tags": [
                        "랜섬웨어",
                        "KEV"
                      ],
                      "counts": {
                        "critical": 10,
                        "new": 134
                      }
                    }
                    """);
        }

        @Test
        @DisplayName("값이 없는 항목도 이름을 남기고 비워 둔다")
        void missingValueKeepsName() throws IOException {
            writer.write(META, new ExportSampleJson("CVE-2026-41203", 0, null, null, null, null, null, false,
                    List.of(), Map.of()));

            assertThat(read("meta.json")).contains("\"epss\": null", "\"kev_due_date\": null",
                    "\"updated_at\": null", "\"tags\": []", "\"counts\": {}");
        }

        @Test
        @DisplayName("작은 소수도 지수 표기 없이 그대로 쓴다")
        void plainDecimals() throws IOException {
            writer.write(META, new ExportSampleJson("CVE-2026-41203", 0, new BigDecimal("0.00001"),
                    new BigDecimal("1E+1"), null, null, null, false, List.of(), Map.of()));

            assertThat(read("meta.json")).contains("\"epss\": 0.00001", "\"epss_percentile\": 10");
        }
    }

    @Nested
    @DisplayName("이미 있는 파일과")
    class Existing {

        @Test
        @DisplayName("내용이 같으면 다시 쓰지 않는다")
        void sameContentUntouched() throws IOException {
            assertThat(writer.write(META, sample("CVE-2026-41203", null))).isEqualTo(WriteOutcome.WRITTEN);
            Files.setLastModifiedTime(dataDir.resolve("meta.json"), LONG_AGO);

            WriteOutcome outcome = writer.write(META, sample("CVE-2026-41203", null));

            assertThat(outcome).isEqualTo(WriteOutcome.UNCHANGED);
            assertThat(Files.getLastModifiedTime(dataDir.resolve("meta.json"))).isEqualTo(LONG_AGO);
        }

        @Test
        @DisplayName("내용이 다르면 새 내용으로 바꾸고 임시 파일을 남기지 않는다")
        void differentContentReplaced() throws IOException {
            writer.write(META, sample("CVE-2026-41203", null));

            WriteOutcome outcome = writer.write(META, sample("CVE-2026-41203", "Chrome"));

            assertThat(outcome).isEqualTo(WriteOutcome.WRITTEN);
            assertThat(read("meta.json")).contains("\"primary_product\": \"Chrome\"");
            try (Stream<Path> files = Files.list(dataDir)) {
                assertThat(files).containsExactly(dataDir.resolve("meta.json"));
            }
        }
    }

    @Test
    @DisplayName("상세 파일은 하위 폴더가 없으면 만들어 쓴다")
    void createsSubFolder() throws IOException {
        writer.write(DataFilePath.of("cve/CVE-2026-41203.json"), sample("CVE-2026-41203", null));

        assertThat(read("cve/CVE-2026-41203.json")).startsWith("{\n  \"id\": \"CVE-2026-41203\"");
    }

    @Test
    @DisplayName("쓸 수 없는 곳이면 출력 실패로 알리고 상세에는 상대 경로만 담는다")
    void unwritable() throws IOException {
        Files.writeString(dataDir.resolve("cve"), "folder 자리에 파일");

        assertThatThrownBy(() -> writer.write(DataFilePath.of("cve/CVE-2026-41203.json"),
                sample("CVE-2026-41203", null)))
                .isInstanceOf(OutputWriteException.class)
                .hasMessageContaining("cve/CVE-2026-41203.json")
                .hasMessageNotContaining(dataDir.toString());
    }

    @Test
    @DisplayName("JSON 으로 옮길 수 없는 문서면 아무것도 쓰지 않고 출력 실패로 알린다")
    void unserializable() {
        assertThatThrownBy(() -> writer.write(META, new Object()))
                .isInstanceOf(OutputWriteException.class)
                .hasMessageContaining("meta.json");
        assertThat(dataDir.resolve("meta.json")).doesNotExist();
    }

    private static ExportSampleJson sample(String id, String product) {
        Map<String, Integer> counts = new TreeMap<>(Map.of("new", 134, "critical", 10));
        return new ExportSampleJson(id, 67, new BigDecimal("0.94310"), new BigDecimal("0.99900"),
                LocalDate.parse("2026-11-03"), OffsetDateTime.of(2026, 10, 31, 9, 0, 0, 0, ZoneOffset.ofHours(9)),
                product, true, List.of("랜섬웨어", "KEV"), counts);
    }

    private String read(String relative) throws IOException {
        return Files.readString(dataDir.resolve(relative), StandardCharsets.UTF_8);
    }
}
