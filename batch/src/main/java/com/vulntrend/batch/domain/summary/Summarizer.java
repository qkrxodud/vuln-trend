package com.vulntrend.batch.domain.summary;

/** 한국어 요약 창구(기획서 6장 7단계). 구현은 infrastructure/summary 에 둔다. 실패해도 던지지 않고 결과로 알린다(비치명) */
public interface Summarizer {

    SummaryResult summarize(SummarySource source);
}
