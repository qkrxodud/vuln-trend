package com.vulntrend.batch.domain.gate.dto;

import java.time.LocalDateTime;

/** 데이터 커밋 하나(GitPusher 가 남긴 "data: yyyy-MM-dd HH:mm KST"): 실행 시각(한국 시각)과 바뀐 데이터 파일 수 */
public record DataCommit(LocalDateTime runAt, int changedFiles) {
}
