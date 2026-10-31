package com.vulntrend.batch.domain.enrichment;

import java.time.Instant;
import java.util.List;

/** 실제 악용 목록(KEV)을 취약점 목록에 반영하는 저장소 */
public interface KevRepository {

    /**
     * 받은 목록이 KEV 전체라고 보고 그대로 맞춘다 — 목록에 있는 CVE 에 표시를 붙이고, 목록에서 내려간 CVE 의 표시는 지운다.
     * 원본은 취약점 목록에 있든 없든 한 건씩 보관한다. 한꺼번에 반영된다
     */
    KevMarking markAll(List<KevEntry> entries, Instant fetchedAt);
}
