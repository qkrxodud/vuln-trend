package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.score.TrendListRepository;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import com.vulntrend.batch.domain.stat.StatDays;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 최근 7일 신규(기획서 7장, WBS 4.2): 오늘 포함 최근 7개 한국 달력일(도넛과 같은 날들)에 발행된 CVE 중 트렌드 순서 상위 100건(결정 대기 (ag)) */
@Component
@RequiredArgsConstructor
public class RecentFileExport {

    private static final DataFilePath PATH = DataFilePath.of("recent.json");
    private static final int LIMIT = 100;
    private static final int DAYS = 7;

    private final TrendListRepository repository;
    private final SiteDataWriter writer;

    /** 파일을 쓰고, 실린 CVE 를 순서대로 알린다(상세 파일 생성 범위, P6) */
    public Listed write(Instant now) {
        Instant from = StatDays.recent(now).getToday().minusDays(DAYS - 1).atStartOfDay(StatDays.CALENDAR).toInstant();
        List<TrendListing> listings = repository.publishedBetween(from, now, LIMIT, now);
        List<TrendRowJson> rows = listings.stream().map(TrendRows::from).toList();
        return Listed.of(writer.write(PATH, rows), listings.stream().map(TrendListing::id).toList());
    }
}
