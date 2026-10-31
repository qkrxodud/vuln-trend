package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.KpiCountsJson;
import com.vulntrend.batch.application.export.dto.MetaJson;
import com.vulntrend.batch.application.stat.KpiService;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.export.WriteOutcome;
import com.vulntrend.batch.domain.stat.KpiCounts;
import com.vulntrend.batch.domain.stat.KpiSummary;
import com.vulntrend.batch.domain.stat.StatDays;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * meta.json (기획서 7장, WBS 4.2, D11·P16). 갱신 시각은 이번 실행 시각을 한국 시각(+09:00) 초 단위로,
 * 판(version)은 한국 시각 yyyyMMdd-HHmm — 프론트가 나머지 파일을 ?v= 로 읽는 캐시 버스팅 값이다.
 * KPI 는 3.4 집계 그대로이고, 어제 고위험을 모르면 비운다(화면은 증감을 숨김).
 */
@Component
@RequiredArgsConstructor
public class MetaFileExport {

    private static final DataFilePath PATH = DataFilePath.of("meta.json");
    private static final DateTimeFormatter VERSION = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final KpiService kpiService;
    private final SiteDataWriter writer;

    public WriteOutcome write(Instant now) {
        OffsetDateTime updatedAt = now.truncatedTo(ChronoUnit.SECONDS).atZone(StatDays.CALENDAR).toOffsetDateTime();
        KpiSummary summary = kpiService.summarize(now);
        MetaJson meta = new MetaJson(updatedAt, VERSION.format(updatedAt), counts(summary.getToday()),
                counts(summary.getYesterday()));
        return writer.write(PATH, meta);
    }

    private static KpiCountsJson counts(KpiCounts counts) {
        return new KpiCountsJson(counts.getNewCount(), counts.getKevNewCount(), counts.getCriticalCount(),
                counts.getHighRiskCount().orElse(null));
    }
}
