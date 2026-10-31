package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.DailyCountJson;
import com.vulntrend.batch.application.export.dto.SeverityDistributionJson;
import com.vulntrend.batch.application.export.dto.StatsJson;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.export.WriteOutcome;
import com.vulntrend.batch.domain.stat.DailyStat;
import com.vulntrend.batch.domain.stat.DailyStatRepository;
import com.vulntrend.batch.domain.stat.StatDays;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * stats.json (기획서 7장·8장 차트, WBS 4.2). 날짜는 daily_stat 의 한국 달력일(3.3).
 * - daily: 최근 14일을 오래된 날부터 정확히 14칸. 통계가 없는 날은 0
 * - severity_7d: 최근 7개 달력일(오늘 포함) 신규의 심각도 다섯 갈래 합(ISO 주 아님, 미평가 포함)
 */
@Component
@RequiredArgsConstructor
public class StatsFileExport {

    private static final DataFilePath PATH = DataFilePath.of("stats.json");
    private static final int SEVERITY_DAYS = 7;

    private final DailyStatRepository repository;
    private final SiteDataWriter writer;

    public WriteOutcome write(Instant now) {
        StatDays days = StatDays.recent(now);
        Map<LocalDate, DailyStat> stored = repository.findBetween(days.getFirst(), days.getToday()).stream()
                .collect(Collectors.toMap(DailyStat::getStatDate, Function.identity()));
        List<DailyStat> fourteen = days.getFirst().datesUntil(days.getToday().plusDays(1))
                .map(date -> stored.getOrDefault(date, DailyStat.empty(date)))
                .toList();
        List<DailyStat> seven = fourteen.subList(fourteen.size() - SEVERITY_DAYS, fourteen.size());
        StatsJson stats = new StatsJson(
                fourteen.stream().map(StatsFileExport::daily).toList(),
                new SeverityDistributionJson(sum(seven, DailyStat::getCriticalCount), sum(seven, DailyStat::getHighCount),
                        sum(seven, DailyStat::getMediumCount), sum(seven, DailyStat::getLowCount),
                        sum(seven, DailyStat::getUnratedCount)));
        return writer.write(PATH, stats);
    }

    private static DailyCountJson daily(DailyStat stat) {
        return new DailyCountJson(stat.getStatDate(), stat.getNewCount(), stat.getCriticalCount());
    }

    private static int sum(List<DailyStat> stats, ToIntFunction<DailyStat> count) {
        return stats.stream().mapToInt(count).sum();
    }
}
