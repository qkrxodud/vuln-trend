package com.vulntrend.batch.domain.cvelist;

import com.vulntrend.batch.domain.vulnerability.CveId;

/** CVE.org 레코드 한 건 조회 창구. 답을 못 받으면 SourceFetchException(소스 cvelist) */
public interface CvelistSource {

    CnaRecord fetch(CveId cveId);
}
