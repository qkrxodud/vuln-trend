package com.vulntrend.batch.application.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.export.dto.SearchEntryJson;
import com.vulntrend.batch.domain.export.dto.SearchListing;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.fake.FakeCveDetailRepository;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("search-index.json")
class SearchIndexFileExportTest {

    /** 한국 시각 2026-11-01 00:30 실행. 12개월 전 날은 2025-11-01 00:00(한국) = 2025-10-31T15:00Z */
    private static final Instant NOW = Instant.parse("2026-10-31T15:30:00Z");

    private final FakeSiteDataWriter writer = new FakeSiteDataWriter();

    @Test
    @DisplayName("최근 12개월 CVE 를 받은 순서대로 번호·제목·제품으로 싣고 제목이 없으면 번호를 쓴다")
    void recentYearInOrder() {
        FakeCveDetailRepository repository = new FakeCveDetailRepository()
                .search(new SearchListing(CveId.from("CVE-2026-41203"), "V8 타입 혼동", "Chrome"),
                        Instant.parse("2026-10-20T00:00:00Z"))
                .search(new SearchListing(CveId.from("CVE-2026-50001"), null, null),
                        Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(export(repository)).containsExactly(new SearchEntryJson("CVE-2026-41203", "V8 타입 혼동", "Chrome"),
                new SearchEntryJson("CVE-2026-50001", "CVE-2026-50001", null));
    }

    @Nested
    @DisplayName("12개월의 경계는")
    class Boundary {

        @Test
        @DisplayName("12개월 전 날 한국 시각 00:00 에 발행된 CVE 는 넣고 그 1초 전 것은 뺀다")
        void startOfDayIncluded() {
            FakeCveDetailRepository repository = new FakeCveDetailRepository()
                    .search(new SearchListing(CveId.from("CVE-2025-60001"), "경계", null),
                            Instant.parse("2025-10-31T15:00:00Z"))
                    .search(new SearchListing(CveId.from("CVE-2025-60002"), "1초 전", null),
                            Instant.parse("2025-10-31T14:59:59Z"));

            assertThat(export(repository)).extracting(SearchEntryJson::id).containsExactly("CVE-2025-60001");
        }

        @Test
        @DisplayName("발행일이 없는 CVE 는 싣지 않는다")
        void unpublishedLeftOut() {
            FakeCveDetailRepository repository = new FakeCveDetailRepository()
                    .search(new SearchListing(CveId.from("CVE-2026-60001"), "발행일 없음", null));

            assertThat(export(repository)).isEmpty();
        }

        @Test
        @DisplayName("시작은 한국 달력일로 정해서 세계 표준시로는 전날 15시다")
        void koreanCalendarDay() {
            assertThat(SearchIndexFileExport.since(NOW)).isEqualTo(Instant.parse("2025-10-31T15:00:00Z"));
            assertThat(SearchIndexFileExport.since(Instant.parse("2026-10-31T14:30:00Z")))
                    .isEqualTo(Instant.parse("2025-10-30T15:00:00Z"));
        }

        @Test
        @DisplayName("그런 날이 없는 달이면 그달 마지막 날부터다")
        void shorterMonth() {
            assertThat(SearchIndexFileExport.since(Instant.parse("2028-02-29T03:00:00Z")))
                    .isEqualTo(Instant.parse("2027-02-27T15:00:00Z"));
            assertThat(SearchIndexFileExport.since(Instant.parse("2026-10-31T03:00:00Z"), 6))
                    .isEqualTo(Instant.parse("2026-04-29T15:00:00Z"));
        }
    }

    @SuppressWarnings("unchecked")
    private List<SearchEntryJson> export(FakeCveDetailRepository repository) {
        new SearchIndexFileExport(repository, writer).write(NOW);
        return writer.document("search-index.json", List.class).orElseThrow();
    }
}
