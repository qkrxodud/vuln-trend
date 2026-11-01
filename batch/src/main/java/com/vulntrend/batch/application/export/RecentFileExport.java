package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.product.ProductNames;
import com.vulntrend.batch.domain.score.TrendListRepository;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import com.vulntrend.batch.domain.stat.StatDays;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 최근 7일 신규(기획서 7장, WBS 4.2, 사용자 결정 2026-11-01 "최근 7일 새 CVE 전체 보기"): 오늘 포함 최근 7개 한국 달력일
 * (도넛과 같은 날들)에 발행된 CVE 전부, 최근 발행 순. 상한은 없다(2026-11-01 실측 약 3,400건). 게시할지 정하는 화면 지문에는 넣지 않는다
 */
@Component
@RequiredArgsConstructor
public class RecentFileExport {

    private static final DataFilePath PATH = DataFilePath.of("recent.json");
    private static final int DAYS = 7;

    private final TrendListRepository repository;
    private final SiteDataWriter writer;

    /** 파일을 쓰고, 실린 CVE 를 순서대로 알린다(상세 파일 생성 범위, P6) */
    public Listed write(Instant now, ProductNames names) {
        Instant from = StatDays.recent(now).getToday().minusDays(DAYS - 1).atStartOfDay(StatDays.CALENDAR).toInstant();
        List<TrendListing> listings = repository.publishedBetween(from, now, now);
        List<TrendRowJson> rows = listings.stream().map(listing -> TrendRows.from(listing, names)).toList();
        return Listed.of(writer.write(PATH, rows), listings.stream().map(TrendListing::id).toList());
    }
}
