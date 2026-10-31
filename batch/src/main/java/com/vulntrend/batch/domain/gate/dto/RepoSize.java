package com.vulntrend.batch.domain.gate.dto;

/** git 저장소 크기(git count-objects -v): 묶인 객체·낱개 객체 바이트 */
public record RepoSize(long packedBytes, long looseBytes) {
}
