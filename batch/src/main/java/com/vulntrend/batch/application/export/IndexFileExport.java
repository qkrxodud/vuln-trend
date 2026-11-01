package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.product.ProductNames;
import com.vulntrend.batch.domain.score.TrendListRepository;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 트렌드 점수 상위 100건(기획서 7장, WBS 4.2). 순서·대상은 TrendOrder 와 같다 — 6.3 요약 대상(상위 20건)과 같은 줄 */
@Component
@RequiredArgsConstructor
public class IndexFileExport {

    private static final DataFilePath PATH = DataFilePath.of("index.json");
    private static final int LIMIT = 100;

    private final TrendListRepository repository;
    private final SiteDataWriter writer;

    /** 파일을 쓰고, 실린 CVE 를 순서대로 알린다(상세 파일 생성 범위, P6) */
    public Listed write(Instant now, ProductNames names) {
        List<TrendListing> listings = repository.top(LIMIT, now);
        List<TrendRowJson> rows = listings.stream().map(listing -> TrendRows.from(listing, names)).toList();
        return Listed.of(writer.write(PATH, rows), listings.stream().map(TrendListing::id).toList());
    }
}
