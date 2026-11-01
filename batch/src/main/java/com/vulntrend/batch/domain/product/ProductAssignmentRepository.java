package com.vulntrend.batch.domain.product;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 대표 제품(vulnerability.primary_product)·제품군(product_family)과 영향받는 제품의 묶음 이름(affected_product.slug)을 읽고 쓰는 저장소 */
public interface ProductAssignmentRepository {

    /** 철회되지 않은 모든 CVE 의 대표 제품 재료 */
    List<PrimaryProductCandidate> findPrimaryCandidates();

    /** 대표 제품을 바꾼다. 값이 없으면 비운다. 바꾼 CVE 수를 돌려준다 */
    int changePrimaryProducts(Map<CveId, Optional<String>> changes);

    /** 제품군(vulnerability.product_family)을 바꾼다. 값이 없으면 비운다(분류 전). 바꾼 CVE 수를 돌려준다 */
    int changeFamilies(Map<CveId, Optional<ProductFamily>> changes);

    /** 영향받는 제품 목록의 (제조사, 제품, 지금 묶음 이름) 조합 */
    List<AffectedProductName> findAffectedProductNames();

    /** 그 제조사·제품이 나오는 모든 줄의 묶음 이름을 바꾼다. 값이 없으면 비운다. 바꾼 줄 수를 돌려준다 */
    int changeSlugs(Map<ProductMention, Optional<String>> changes);
}
