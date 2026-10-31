package com.vulntrend.batch.domain.export;

import java.util.List;

/** 사이트 데이터 파일 쓰기(WBS 4.1·4.2). 문서는 JSON 계약(web/src/types) 모양의 값이다 */
public interface SiteDataWriter {

    WriteOutcome write(DataFilePath path, Object document);

    /** 데이터 폴더 아래 그 하위 폴더(예: cve)에 있는 JSON 파일들. 폴더가 없으면 비어 있다 */
    List<DataFilePath> listFolder(String folder);

    /** 그 파일을 지운다. 없으면 아무 일도 하지 않고 false */
    boolean remove(DataFilePath path);
}
