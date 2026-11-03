package com.vulntrend.batch.domain.cvelist;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 저장된 CNA affected 읽기 창구(3b 대표 제품 선정이 쓴다, 백엔드 B 와 합의 2026-11-02). 받은 순서(position)대로 돌려준다.
 * CVE.org 에서 답을 받은 적이 없는 CVE 는 맵에 키가 없다. 답을 받았지만 affected 가 없었으면 키가 있고 목록이 비어 있다
 * ("제품 정보 대기"를 셀 때 이 구분을 쓴다).
 */
public interface CnaAffectedRepository {

    Map<CveId, List<CnaAffected>> findByCveIds(Collection<CveId> cveIds);
}
