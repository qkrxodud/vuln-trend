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

    /** 검색 색인 중 발행 시각이 from 이후인 것(같은 순서). 발행일이 없는 건은 빠진다 — Gate G 기간별 크기 실측용 */
    List<SearchListing> searchEntriesSince(Instant from);

    /** 제품 표(product_alias)의 표시 이름 전부 — 사이트에 보이는 제품 이름(ProductNames)이 표에 적힌 대로 두는 이름 */
    List<String> standardProductNames();

    /** 트렌드 순서 상위 limit 건의 번호 */
    List<CveId> topIds(int limit);

    /** 그 CVE 들의 상세(없거나 철회된 번호는 빠진다, 순서는 정하지 않음) */
    List<CveDetailListing> details(Collection<CveId> ids, Instant now);
}
