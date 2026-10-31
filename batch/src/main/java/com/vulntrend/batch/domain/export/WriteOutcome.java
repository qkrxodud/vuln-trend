package com.vulntrend.batch.domain.export;

/** 파일 하나를 쓴 결과. 내용이 같으면 쓰지 않는다(기획서 7장 원칙, WBS 4.1) */
public enum WriteOutcome {
    WRITTEN,
    UNCHANGED
}
