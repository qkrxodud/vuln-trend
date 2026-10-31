package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.SearchEntryJson;
import com.vulntrend.batch.domain.export.CveDetailRepository;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.export.WriteOutcome;
import com.vulntrend.batch.domain.export.dto.SearchListing;
import com.vulntrend.batch.domain.stat.StatDays;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * search-index.json (기획서 7장 P19, WBS 4.2, R8 사용자 결정 2026-11-01): 철회되지 않은 CVE 중 최근 12개월에 발행된 것,
 * 최근 발행 순. 12개월은 이번 실행의 한국 달력일에서 12개월 전 날 00:00(한국 시각)부터(그 시각 포함), 발행일이 없는 건은 뺀다.
 * 번호·제목·대표 제품만 싣고, 제목이 아직 없으면 번호를 쓴다(목록과 같은 규칙). 모양은 다른 파일과 같다(들여쓰기) — 크기 기준은 gzip.
 */
@Component
@RequiredArgsConstructor
public class SearchIndexFileExport {

    private static final int MONTHS = 12;
    private static final DataFilePath PATH = DataFilePath.of("search-index.json");

    private final CveDetailRepository repository;
    private final SiteDataWriter writer;

    public WriteOutcome write(Instant now) {
        List<SearchEntryJson> entries = repository.searchEntriesSince(since(now)).stream()
                .map(SearchIndexFileExport::entry)
                .toList();
        return writer.write(PATH, entries);
    }

    /** 검색 색인에 싣는 발행 시각의 시작(포함). 이번 실행의 한국 달력일에서 12개월 전 날 00:00(한국 시각) */
    public static Instant since(Instant now) {
        return since(now, MONTHS);
    }

    /** 이번 실행의 한국 달력일에서 months 개월 전 날 00:00(한국 시각). 그런 날이 없으면 그달 마지막 날(예: 2월 29일 → 2월 28일) */
    public static Instant since(Instant now, int months) {
        return LocalDate.ofInstant(now, StatDays.CALENDAR).minusMonths(months).atStartOfDay(StatDays.CALENDAR).toInstant();
    }

    /** 검색 한 건의 JSON 모양. Gate G 크기 실측도 이 모양으로 잰다 */
    public static SearchEntryJson entry(SearchListing listing) {
        return new SearchEntryJson(listing.id().getValue(),
                Optional.ofNullable(listing.title()).orElse(listing.id().getValue()), listing.product());
    }
}
