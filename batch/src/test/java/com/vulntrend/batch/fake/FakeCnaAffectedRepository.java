package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.cvelist.CnaAffected;
import com.vulntrend.batch.domain.cvelist.CnaAffectedRepository;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 저장된 CNA affected 흉내. 넣어 둔 것(답을 받은 CVE)만 돌려준다. 빈 목록 = 답은 받았지만 affected 없음 */
public class FakeCnaAffectedRepository implements CnaAffectedRepository {

    private final Map<CveId, List<CnaAffected>> affected = new LinkedHashMap<>();

    public FakeCnaAffectedRepository with(CveId id, List<CnaAffected> list) {
        affected.put(id, List.copyOf(list));
        return this;
    }

    @Override
    public Map<CveId, List<CnaAffected>> findByCveIds(Collection<CveId> cveIds) {
        Map<CveId, List<CnaAffected>> found = new LinkedHashMap<>();
        cveIds.stream().filter(affected::containsKey).forEach(id -> found.put(id, affected.get(id)));
        return found;
    }
}
