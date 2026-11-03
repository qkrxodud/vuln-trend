package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.cvelist.CnaAffected;
import com.vulntrend.batch.domain.cvelist.CnaRecord;
import com.vulntrend.batch.domain.cvelist.CvelistSource;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CVE.org 흉내. 넣어 둔 affected 를 돌려주고, 넣지 않은 CVE 는 답하지 못한다(SourceFetchException). 부른 순서를 기억한다 */
public class FakeCvelistSource implements CvelistSource {

    private final Map<CveId, List<CnaAffected>> answers = new HashMap<>();
    private final List<CveId> asked = new ArrayList<>();

    public FakeCvelistSource answering(CveId id, List<CnaAffected> affected) {
        answers.put(id, affected);
        return this;
    }

    @Override
    public CnaRecord fetch(CveId cveId) {
        asked.add(cveId);
        if (answers.containsKey(cveId)) {
            return CnaRecord.of(cveId, answers.get(cveId));
        }
        throw new SourceFetchException("cvelist", "HTTP 503.");
    }

    public List<CveId> asked() {
        return List.copyOf(asked);
    }
}
