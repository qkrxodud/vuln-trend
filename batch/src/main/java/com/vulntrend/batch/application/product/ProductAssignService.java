package com.vulntrend.batch.application.product;

import com.vulntrend.batch.domain.product.AffectedProductName;
import com.vulntrend.batch.domain.product.PrimaryProductCandidate;
import com.vulntrend.batch.domain.product.PrimaryProductRule;
import com.vulntrend.batch.domain.product.ProductAliasCatalog;
import com.vulntrend.batch.domain.product.ProductAssignmentRepository;
import com.vulntrend.batch.domain.product.ProductFamily;
import com.vulntrend.batch.domain.product.ProductFamilyRule;
import com.vulntrend.batch.domain.product.ProductMention;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 대표 제품과 영향받는 제품의 묶음 이름 매기기(WBS 2.3). 수집(NVD·KEV·EPSS)이 모두 성공한 뒤, 점수 단계 전에 하루 한 번 돈다
 * (러너 연결은 백엔드 A 의 2.4). 표는 원본(product-alias.yml)에서 읽는다 — 기동 시 product_alias 에 적재한 것과 같은 내용이다.
 * 표를 고치면 다음 실행에서 모든 CVE 가 새 표로 다시 매겨진다. 달라진 것만 쓴다.
 * 제품군(사용자 결정 2026-11-01)도 여기서 같은 재료로 매긴다 — 규칙은 ProductFamilyRule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAssignService {

    private final ProductAliasCatalog catalog;
    private final ProductAssignmentRepository repository;

    public ProductAssignment assignAll() {
        PrimaryProductRule rule = PrimaryProductRule.of(catalog.load());
        List<PrimaryProductCandidate> candidates = repository.findPrimaryCandidates();
        int primaryChanged = repository.changePrimaryProducts(primaryChanges(rule, candidates));
        int slugChanged = repository.changeSlugs(slugChanges(rule));
        int familyChanged = repository.changeFamilies(familyChanges(rule, candidates));
        log.info("Primary product changed for {} CVEs; product slug changed on {} affected product rows; "
                + "product family changed for {} CVEs.", primaryChanged, slugChanged, familyChanged);
        return ProductAssignment.of(primaryChanged, slugChanged, familyChanged);
    }

    private Map<CveId, Optional<ProductFamily>> familyChanges(PrimaryProductRule rule,
            List<PrimaryProductCandidate> candidates) {
        Map<CveId, Optional<ProductFamily>> changes = new LinkedHashMap<>();
        for (PrimaryProductCandidate candidate : candidates) {
            Optional<ProductFamily> family = ProductFamilyRule.classify(
                    rule.familyOf(candidate.getKev(), candidate.getFirstCpe()), candidate.getFirstCpeCriteria(),
                    candidate.getCna());
            if (family.equals(candidate.getCurrentFamily())) {
                continue;
            }
            changes.put(candidate.getCveId(), family);
        }
        return changes;
    }

    private Map<CveId, Optional<String>> primaryChanges(PrimaryProductRule rule,
            List<PrimaryProductCandidate> candidates) {
        Map<CveId, Optional<String>> changes = new LinkedHashMap<>();
        for (PrimaryProductCandidate candidate : candidates) {
            Optional<String> chosen = rule.choose(candidate.getKev(), candidate.getFirstCpe());
            if (chosen.equals(candidate.getCurrentPrimary())) {
                continue;
            }
            changes.put(candidate.getCveId(), chosen);
        }
        return changes;
    }

    private Map<ProductMention, Optional<String>> slugChanges(PrimaryProductRule rule) {
        Map<ProductMention, Optional<String>> changes = new LinkedHashMap<>();
        for (AffectedProductName name : repository.findAffectedProductNames()) {
            Optional<String> slug = rule.slugOf(name.getMention());
            if (slug.equals(name.getCurrentSlug())) {
                continue;
            }
            changes.put(name.getMention(), slug);
        }
        return changes;
    }
}
