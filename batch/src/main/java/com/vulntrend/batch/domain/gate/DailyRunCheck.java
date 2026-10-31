package com.vulntrend.batch.domain.gate;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.gate.dto.DataCommit;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Gate G "배치 3일 연속 성공"(WBS 9장). 실행 기록 표가 없어서 두 근거를 함께 본다(결정 대기):
 * ① 데이터 커밋 — GitPusher 는 1~8단계가 모두 성공한 실행에서만 커밋하고, meta.json 의 갱신 시각이 매번 바뀌므로 성공한 실행마다
 *    "data: yyyy-MM-dd HH:mm KST" 커밋이 하나 남는다. 그 한국 달력일이 사흘 연속이고, 마지막이 오늘이나 어제여야 한다.
 * ② 소스 진행 기록(sync_state) — MVP 소스(NVD·KEV·EPSS)의 마지막 실행이 모두 성공이어야 한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DailyRunCheck {

    public static final String ITEM = "배치 3일 연속 성공";
    public static final List<SourceName> MVP_SOURCES = List.of(SourceName.NVD, SourceName.KEV, SourceName.EPSS);
    private static final int DAYS = 3;
    private static final ZoneId SITE_CALENDAR = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static GateItem judge(List<DataCommit> commits, List<SyncState> states, Instant now) {
        List<String> evidence = new ArrayList<>();
        evidence.add("데이터 커밋: " + commits.stream().limit(5).map(c -> MINUTE.format(c.runAt()))
                .collect(Collectors.joining(", ")));
        states.forEach(state -> evidence.add(state.getSource().getKey() + ": " + state.getStatus()
                + state.getLastRunAt()
                        .map(at -> ", 마지막 실행 " + MINUTE.format(at.atZone(SITE_CALENDAR)) + " KST").orElse("")));
        List<String> unhealthy = states.stream()
                .filter(state -> state.getStatus() != SyncStatus.SUCCESS)
                .map(state -> state.getSource().getKey() + " " + state.getStatus())
                .toList();
        if (unhealthy.size() > 0) {
            return GateItem.of(ITEM, GateStatus.FAIL, "마지막 실행이 성공이 아닌 소스: " + String.join(", ", unhealthy),
                    evidence);
        }
        List<LocalDate> days = commits.stream().map(c -> c.runAt().toLocalDate()).distinct()
                .sorted(Comparator.reverseOrder()).toList();
        if (days.size() < DAYS) {
            return GateItem.of(ITEM, GateStatus.FAIL, "데이터 커밋이 " + days.size() + "일뿐", evidence);
        }
        LocalDate today = LocalDate.ofInstant(now, SITE_CALENDAR);
        LocalDate latest = days.get(0);
        if (latest.isBefore(today.minusDays(1))) {
            return GateItem.of(ITEM, GateStatus.FAIL, "마지막 성공이 " + latest + " — 어제 이후 성공 없음", evidence);
        }
        Optional<LocalDate> missing = LongStream.range(1, DAYS).mapToObj(latest::minusDays)
                .filter(Predicate.not(days::contains))
                .findFirst();
        return missing
                .map(day -> GateItem.of(ITEM, GateStatus.FAIL, day + " 에 성공한 실행이 없음", evidence))
                .orElseGet(() -> GateItem.of(ITEM, GateStatus.PASS,
                        latest.minusDays(DAYS - 1) + " ~ " + latest + " 매일 성공", evidence));
    }
}
