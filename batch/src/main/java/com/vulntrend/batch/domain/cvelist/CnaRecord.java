package com.vulntrend.batch.domain.cvelist;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** CVE.org 에서 받은 한 CVE 의 CNA affected 목록. 비어 있을 수 있다(CNA 가 적지 않았거나 거부된 CVE) */
@Getter
public class CnaRecord {

    private final CveId cveId;
    private final List<CnaAffected> affected;

    @Builder(access = AccessLevel.PRIVATE)
    private CnaRecord(CveId cveId, List<CnaAffected> affected) {
        this.cveId = cveId;
        this.affected = List.copyOf(affected);
    }

    public static CnaRecord of(CveId cveId, List<CnaAffected> affected) {
        return CnaRecord.builder().cveId(cveId).affected(affected).build();
    }

    public boolean hasAffected() {
        return affected.size() > 0;
    }

    /** 저장된 목록과 순서·칸까지 같으면 다시 쓰지 않는다(공통 조건 "내용이 같으면 다시 쓰지 않는다") */
    public boolean isSameAs(List<CnaAffected> stored) {
        return affected.equals(stored);
    }
}
