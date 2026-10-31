package com.vulntrend.batch.application.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import com.vulntrend.batch.fake.FakeTrendListRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("index.json")
class IndexFileExportTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Test
    @DisplayName("트렌드 순서 상위 100건을 이번 실행 시각 기준 점수로 싣고 실린 CVE 를 순서대로 알린다")
    void topHundred() {
        FakeTrendListRepository repository = new FakeTrendListRepository()
                .with(TrendListings.kev("CVE-2026-41203"))
                .with(TrendListings.unrated("CVE-2026-50001"));
        FakeSiteDataWriter writer = new FakeSiteDataWriter();

        Listed listed = new IndexFileExport(repository, writer).write(NOW);

        assertThat(repository.askedLimit()).isEqualTo(100);
        assertThat(repository.askedNow()).isEqualTo(NOW);
        assertThat(listed.getIds()).containsExactly(CveId.from("CVE-2026-41203"), CveId.from("CVE-2026-50001"));
        @SuppressWarnings("unchecked")
        List<TrendRowJson> rows = writer.document("index.json", List.class).orElseThrow();
        assertThat(rows).extracting(TrendRowJson::id).containsExactly("CVE-2026-41203", "CVE-2026-50001");
    }
}
