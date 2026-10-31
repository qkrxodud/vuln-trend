package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.SearchEntryJson;
import com.vulntrend.batch.domain.export.CveDetailRepository;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.export.WriteOutcome;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * search-index.json (기획서 7장 P19, WBS 4.2): 철회되지 않은 전체 CVE, 최근 발행 순. 번호·제목·대표 제품만 싣는다.
 * 제목이 아직 없으면 번호를 쓴다(목록과 같은 규칙).
 */
@Component
@RequiredArgsConstructor
public class SearchIndexFileExport {

    private static final DataFilePath PATH = DataFilePath.of("search-index.json");

    private final CveDetailRepository repository;
    private final SiteDataWriter writer;

    public WriteOutcome write() {
        List<SearchEntryJson> entries = repository.searchEntries().stream()
                .map(entry -> new SearchEntryJson(entry.id().getValue(),
                        Optional.ofNullable(entry.title()).orElse(entry.id().getValue()), entry.product()))
                .toList();
        return writer.write(PATH, entries);
    }
}
