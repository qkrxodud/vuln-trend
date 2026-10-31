package com.vulntrend.batch.domain.export;

/** 사이트 데이터 파일 쓰기(WBS 4.1). 문서는 JSON 계약(web/src/types) 모양의 값이다 */
public interface SiteDataWriter {

    WriteOutcome write(DataFilePath path, Object document);
}
