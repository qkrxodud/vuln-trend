package com.vulntrend.batch.domain.gate.dto;

/** 이미 써 둔 파일의 크기: 파일 바이트와 gzip(레벨 6) 바이트 */
public record StoredFileSize(long bytes, long gzipBytes) {
}
