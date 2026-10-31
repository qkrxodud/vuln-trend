package com.vulntrend.batch.domain.export;

/** 문서를 사이트 데이터 JSON 으로 썼을 때의 바이트 수(Gate G 크기 실측). 파일은 쓰지 않는다 */
public interface DocumentSizer {

    long prettyBytes(Object document);

    long compactBytes(Object document);

    /** 지금 모양을 gzip(레벨 6)으로 묶었을 때 — 사이트가 실제로 보내는 크기에 가깝다(R8) */
    long gzipBytes(Object document);
}
