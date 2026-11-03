package com.vulntrend.batch.support;

import com.vulntrend.batch.application.cvelist.CvelistEnrichService;
import com.vulntrend.batch.fake.FakeCnaAffectedRepository;
import com.vulntrend.batch.fake.FakeCvelistFetchRepository;
import com.vulntrend.batch.fake.FakeCvelistSource;
import com.vulntrend.batch.global.config.CvelistProperties;
import java.time.Clock;
import java.time.Duration;

/** 러너를 손으로 조립하는 시험용 CVE.org 보강 단계. 채울 대상이 없어 아무것도 부르지 않는다 */
public final class Cvelists {

    private Cvelists() {
    }

    public static CvelistEnrichService enrichingNothing(Clock clock) {
        FakeCnaAffectedRepository stored = new FakeCnaAffectedRepository();
        return new CvelistEnrichService(properties(), new FakeCvelistSource(), new FakeCvelistFetchRepository(stored),
                stored, clock);
    }

    public static CvelistProperties properties() {
        return new CvelistProperties("http://127.0.0.1:9/unreachable", Duration.ofDays(30), 500, Duration.ofDays(1),
                10, Duration.ZERO, 1, Duration.ZERO, Duration.ofSeconds(1));
    }
}
