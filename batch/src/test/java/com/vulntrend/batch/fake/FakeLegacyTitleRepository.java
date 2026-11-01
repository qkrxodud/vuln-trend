package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.LegacyTitle;
import com.vulntrend.batch.domain.vulnerability.LegacyTitleRepository;
import com.vulntrend.batch.domain.vulnerability.TitleRenewal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 옛 제목 저장소 흉내. CVE 번호 순으로 넘겨 주고, 넘겨 본 쪽 수와 적용한 바꾸기를 기억한다 */
public class FakeLegacyTitleRepository implements LegacyTitleRepository {

    private final List<LegacyTitle> titles;
    private final List<TitleRenewal> applied = new ArrayList<>();
    private int pages;

    public FakeLegacyTitleRepository(List<LegacyTitle> titles) {
        this.titles = titles.stream().sorted(Comparator.comparing(t -> t.getCveId().getValue())).toList();
    }

    @Override
    public List<LegacyTitle> findAfter(Optional<CveId> after, int limit) {
        pages++;
        String from = after.map(CveId::getValue).orElse("");
        return titles.stream().filter(t -> t.getCveId().getValue().compareTo(from) > 0).limit(limit).toList();
    }

    @Override
    public int apply(List<TitleRenewal> renewals) {
        applied.addAll(renewals);
        return renewals.size();
    }

    public List<TitleRenewal> applied() {
        return List.copyOf(applied);
    }

    public int pages() {
        return pages;
    }
}
