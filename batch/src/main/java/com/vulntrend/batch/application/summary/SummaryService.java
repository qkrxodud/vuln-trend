package com.vulntrend.batch.application.summary;

import com.vulntrend.batch.global.config.SummaryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 한국어 요약(기획서 6장 7단계). 비치명 단계 — 열쇠·모델 이름이 없으면 경고만 남기고 0건으로 끝내 다음 단계가 이어진다.
 * 요약을 실제로 만드는 부분(상위 20건 중 요약 없는 것, 일 상한 20콜, 가드)은 WBS 6.2·6.3 에서 이 자리에 붙인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

    private final SummaryProperties properties;

    public int summarizeTop() {
        if (properties.isNotConfigured()) {
            log.warn("Korean summary is skipped: SUMMARY_API_KEY or SUMMARY_MODEL is not set. The site shows the English description instead.");
            return 0;
        }
        log.info("Korean summary with model {} is not wired yet; no summaries were generated this run.",
                properties.getModel());
        return 0;
    }
}
