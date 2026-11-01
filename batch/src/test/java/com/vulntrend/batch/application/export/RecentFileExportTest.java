package com.vulntrend.batch.application.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.product.ProductNames;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import com.vulntrend.batch.fake.FakeTrendListRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("recent.json")
class RecentFileExportTest {

    private static final ProductNames NAMES = ProductNames.of(List.of());

    /** 한국 시각 2026-10-31 09:00 실행. 최근 7일은 10-25 00:00(한국) 부터 */
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Test
    @DisplayName("오늘 포함 최근 7개 한국 달력일에 발행된 CVE 중 트렌드 순서 상위 100건을 싣는다")
    void recentSevenCalendarDays() {
        FakeTrendListRepository repository = new FakeTrendListRepository()
                .with(TrendListings.unrated("CVE-2026-50001"));
        FakeSiteDataWriter writer = new FakeSiteDataWriter();

        Listed listed = new RecentFileExport(repository, writer).write(NOW, NAMES);

        assertThat(repository.askedFrom()).isEqualTo(Instant.parse("2026-10-24T15:00:00Z"));
        assertThat(repository.askedTo()).isEqualTo(NOW);
        assertThat(repository.askedLimit()).isEqualTo(100);
        assertThat(listed.getIds()).hasSize(1);
        @SuppressWarnings("unchecked")
        List<TrendRowJson> rows = writer.document("recent.json", List.class).orElseThrow();
        assertThat(rows).extracting(TrendRowJson::id).containsExactly("CVE-2026-50001");
    }
}
