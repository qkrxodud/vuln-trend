package com.vulntrend.batch.domain.remedy;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 업데이트할 버전(사용자 피드백 "조치 방법이 없다", 승인 2026-11-01). 취약 범위(affected_product.vulnerable_range, 갈래는 "||",
 * 조건은 ",")에서 미만 상한 "<X" 의 X 를 범위에 나온 순서로, 겹치지 않게 뽑는다.
 * 이하 상한 "<=X" 는 고친 버전을 알 수 없고, 특정 버전 "=X"·하한 ">=X" 는 고친 버전이 아니므로 뽑지 않는다. 모르면 빈 목록
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FixVersions {

    private static final String BELOW = "<";
    private static final String AT_MOST = "<=";

    public static List<String> from(String range) {
        if (Objects.isNull(range)) {
            return List.of();
        }
        return Arrays.stream(range.split("\\|\\||,"))
                .map(String::trim)
                .filter(condition -> condition.startsWith(BELOW))
                .filter(Predicate.not(condition -> condition.startsWith(AT_MOST)))
                .map(condition -> condition.substring(BELOW.length()).trim())
                .filter(Predicate.not(String::isEmpty))
                .distinct()
                .toList();
    }

    /** 여러 범위를 차례로 뽑아 겹치지 않게 모은다 */
    public static List<String> fromAll(Collection<String> ranges) {
        return ranges.stream().flatMap(range -> from(range).stream()).distinct().toList();
    }
}
