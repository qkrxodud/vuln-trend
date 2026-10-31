package com.vulntrend.batch.infrastructure.epss;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.enrichment.EpssScore;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.global.error.exception.DomainValidationException;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * EPSS 점수 파일 한 번 읽기(실행마다 새로 만든다, 빈 아님). 줄을 하나씩 받아 취약점 목록에 있는 CVE 의 점수만 모은다.
 * 파일 모양(2026-10-31 확인): "#model_version:…,score_date:…" 주석 줄, "cve,epss,percentile" 머리글, 그 뒤 점수 줄.
 * 머리글이 다르거나 점수 줄이 하나도 없으면 실패다. 모양이 틀린 점수 줄은 건너뛰고 수만 남긴다.
 */
@Slf4j
public class EpssReading {

    private static final String SOURCE = SourceName.EPSS.getKey();
    private static final String COMMENT = "#";
    private static final String HEADER = "cve,epss,percentile";
    private static final int COLUMNS = 3;

    private final Set<CveId> wanted;
    private final List<EpssScore> scores = new ArrayList<>();
    private boolean waitingForHeader = true;
    @Getter
    private int rows;
    private int skipped;

    @Builder(access = AccessLevel.PRIVATE)
    private EpssReading(Set<CveId> wanted) {
        this.wanted = wanted;
    }

    public static EpssReading keeping(Set<CveId> wanted) {
        return EpssReading.builder().wanted(Set.copyOf(wanted)).build();
    }

    public void accept(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith(COMMENT)) {
            return;
        }
        if (waitingForHeader) {
            readHeader(trimmed);
            return;
        }
        rows++;
        readRow(trimmed);
    }

    public List<EpssScore> finish() {
        if (waitingForHeader || rows == 0) {
            throw new SourceFetchException(SOURCE, "the score file had no header or no score rows.");
        }
        if (skipped > 0) {
            log.warn("EPSS file had {} rows that could not be read; they were skipped.", skipped);
        }
        return List.copyOf(scores);
    }

    private void readHeader(String line) {
        if (HEADER.equals(line.toLowerCase(Locale.ROOT))) {
            waitingForHeader = false;
            return;
        }
        throw new SourceFetchException(SOURCE, "unexpected header; expected " + HEADER + ".");
    }

    private void readRow(String line) {
        String[] columns = line.split(",", -1);
        if (columns.length != COLUMNS) {
            skipped++;
            return;
        }
        try {
            keepIfWanted(CveId.from(columns[0]), columns[1], columns[2]);
        } catch (DomainValidationException | NumberFormatException unreadable) {
            skipped++;
        }
    }

    private void keepIfWanted(CveId id, String score, String percentile) {
        if (wanted.contains(id)) {
            scores.add(EpssScore.of(id, new BigDecimal(score.trim()), new BigDecimal(percentile.trim())));
        }
    }
}
