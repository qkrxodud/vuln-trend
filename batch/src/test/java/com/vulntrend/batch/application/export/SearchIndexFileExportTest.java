package com.vulntrend.batch.application.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.export.dto.SearchEntryJson;
import com.vulntrend.batch.domain.export.dto.SearchListing;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.fake.FakeCveDetailRepository;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("search-index.json")
class SearchIndexFileExportTest {

    @Test
    @DisplayName("전체 CVE 를 받은 순서대로 번호·제목·제품으로 싣고 제목이 없으면 번호를 쓴다")
    void everyCveInOrder() {
        FakeCveDetailRepository repository = new FakeCveDetailRepository()
                .search(new SearchListing(CveId.from("CVE-2026-41203"), "V8 타입 혼동", "Chrome"))
                .search(new SearchListing(CveId.from("CVE-2026-50001"), null, null));
        FakeSiteDataWriter writer = new FakeSiteDataWriter();

        new SearchIndexFileExport(repository, writer).write();

        @SuppressWarnings("unchecked")
        List<SearchEntryJson> entries = writer.document("search-index.json", List.class).orElseThrow();
        assertThat(entries).containsExactly(new SearchEntryJson("CVE-2026-41203", "V8 타입 혼동", "Chrome"),
                new SearchEntryJson("CVE-2026-50001", "CVE-2026-50001", null));
    }
}
