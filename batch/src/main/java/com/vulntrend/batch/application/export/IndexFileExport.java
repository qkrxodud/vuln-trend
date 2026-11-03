package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.product.ProductNames;
import com.vulntrend.batch.domain.score.TrendListRepository;
import com.vulntrend.batch.domain.vulnerability.CveId;
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

    /** 파일을 쓰고, 실린 CVE 를 순서대로 알린다(상세 파일 생성 범위, P6). 점수 감쇠는 decayAt(S4) 으로 센다 */
    public Listed write(Instant decayAt, ProductNames names) {
        List<TrendRowJson> rows = rows(decayAt, names);
        return Listed.of(writer.write(PATH, rows), rows.stream().map(row -> CveId.from(row.id())).toList());
    }

    /** 파일에 실을 줄(쓰지 않는다). 게시할지 정하는 화면 지문도 이 줄로 만든다 */
    public List<TrendRowJson> rows(Instant decayAt, ProductNames names) {
        return repository.top(LIMIT, decayAt).stream().map(listing -> TrendRows.from(listing, names)).toList();
    }
}
