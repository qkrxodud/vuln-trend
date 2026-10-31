package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.export.DocumentSizer;
import java.util.List;

/** 크기 재기 흉내. 목록이면 한 줄에 들여쓰기 100바이트·줄바꿈 없이 60바이트·gzip 20바이트로 센다 */
public class FakeDocumentSizer implements DocumentSizer {

    @Override
    public long prettyBytes(Object document) {
        return ((List<?>) document).size() * 100L;
    }

    @Override
    public long compactBytes(Object document) {
        return ((List<?>) document).size() * 60L;
    }

    @Override
    public long gzipBytes(Object document) {
        return ((List<?>) document).size() * 20L;
    }
}
