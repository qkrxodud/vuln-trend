package com.vulntrend.batch.domain.publish;

/** 게시 결과. 데이터 파일이 그대로면 커밋도 push 도 하지 않는다(UNCHANGED) */
public enum PublishResult {
    PUBLISHED, UNCHANGED
}
