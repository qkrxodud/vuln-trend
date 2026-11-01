package com.vulntrend.batch.application.export.dto;

import java.util.List;

/** categories.json (사용자 결정 2026-11-01): 공격 유형·제품군마다 건수와 트렌드 상위 */
public record CategoriesJson(List<CategoryJson> attackTypes, List<CategoryJson> productFamilies) {
}
