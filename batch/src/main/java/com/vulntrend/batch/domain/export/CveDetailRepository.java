package com.vulntrend.batch.domain.export;

import com.vulntrend.batch.domain.export.dto.CveDetailListing;
import com.vulntrend.batch.domain.export.dto.SearchListing;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 상세·검색 파일용 조회(WBS 4.2). 모두 철회 건을 뺀다 */
public interface CveDetailRepository {

    /** 검색 색인: 철회되지 않은 전체 CVE, 최근 발행 순(발행일이 없으면 맨 뒤) → 번호 순 */
    List<SearchListing> searchEntries();

    /** 트렌드 순서 상위 limit 건의 번호 */
    List<CveId> topIds(int limit);

    /** 그 CVE 들의 상세(없거나 철회된 번호는 빠진다, 순서는 정하지 않음) */
    List<CveDetailListing> details(Collection<CveId> ids, Instant now);
}
