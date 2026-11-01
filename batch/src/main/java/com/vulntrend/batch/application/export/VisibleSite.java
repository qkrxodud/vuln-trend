package com.vulntrend.batch.application.export;

import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.export.VisibleFingerprint;
import com.vulntrend.batch.domain.stat.KevAddedWeeks;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 게시할지 정하는 화면 지문(사용자 승인 2026-11-01, 판정·저장은 백엔드 A). 화면이 보여 주는 값만 담는다.
 * - 주목 100건(index.json)의 순서, 행마다 정수 점수·심각도·KEV 여부·조치 기한·랜섬웨어·등재일·요약 유무·업데이트할 버전·패치 링크
 * - 상세의 요약 유무는 같은 CVE 의 summary_what 유무와 같아 행 값으로 대신한다
 * - meta.json 의 최근 7일·그 전 7일 KEV 등재 수
 * 계속 바뀌는 값(갱신 시각·판 이름·24시간 KPI)과 요약 글 자체·악용 확률·제목·제품 이름은 넣지 않는다
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VisibleSite {

    private static final String SEPARATOR = "\t";
    private static final String NONE = "-";

    public static String fingerprint(List<TrendRowJson> rows, KevAddedWeeks kevAdded) {
        List<String> lines = new ArrayList<>(rows.stream().map(VisibleSite::line).toList());
        lines.add("kev_added_7d" + SEPARATOR + kevAdded.getThisWeek() + SEPARATOR + kevAdded.getPreviousWeek());
        return VisibleFingerprint.of(lines);
    }

    private static String line(TrendRowJson row) {
        return Stream.of(row.id(), row.score(), row.severity(), row.isKev(), row.kevDueDate(), row.kevRansomware(),
                        row.kevAddedAt(), Objects.nonNull(row.summaryWhat()), String.join(",", row.fixVersions()),
                        row.patchUrl())
                .map(value -> Optional.ofNullable(value).map(String::valueOf).orElse(NONE))
                .collect(Collectors.joining(SEPARATOR));
    }
}
