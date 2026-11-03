package com.vulntrend.batch.domain.cvelist;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.util.List;

/**
 * CVE.org 보강의 조회 상태와 저장(S3a).
 * 대상: 대표 제품이 없고 거부되지 않은 publishedSince 이후 CVE 중 한 번도 부르지 않았거나,
 * 아직 affected 를 못 채웠고 마지막 시도가 retryBefore 보다 앞선 것. 최근 발행 순, 최대 limit 건.
 */
public interface CvelistFetchRepository {

    List<CveId> findTargets(Instant publishedSince, Instant retryBefore, int limit);

    /** 그 CVE 의 affected 를 통째로 바꾼다 */
    void replaceAffected(CveId cveId, List<CnaAffected> affected);

    /** 답을 받았다(내용이 같아 쓰지 않았어도) */
    void markAnswered(CveId cveId, Instant at);

    /** 답을 받지 못했다 — 재시도 간격 뒤에 다시 대상이 된다 */
    void markFailed(CveId cveId, Instant at);
}
