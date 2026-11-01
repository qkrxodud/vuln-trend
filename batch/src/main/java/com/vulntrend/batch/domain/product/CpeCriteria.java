package com.vulntrend.batch.domain.product;

import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * NVD 의 CPE 2.3 표기 한 줄(cpe:2.3:part:vendor:product:version:update:edition:language:sw_edition:target_sw:target_hw:other).
 * 제품군 규칙이 쓰는 칸만 읽는다. 이름 안의 쌍점은 "\:" 로 이스케이프돼 있어 칸을 나누지 않는다(값은 이스케이프된 그대로)
 */
@Getter
public class CpeCriteria {

    private static final String PREFIX = "cpe:2.3:";
    private static final Pattern FIELD = Pattern.compile("(?<!\\\\):");
    private static final int FIELDS = 13;

    private final String part;
    private final String vendor;
    private final String product;
    private final String targetSoftware;

    @Builder(access = AccessLevel.PRIVATE)
    private CpeCriteria(String part, String vendor, String product, String targetSoftware) {
        this.part = part;
        this.vendor = vendor;
        this.product = product;
        this.targetSoftware = targetSoftware;
    }

    public static Optional<CpeCriteria> parse(String criteria) {
        return Optional.ofNullable(criteria)
                .filter(text -> text.startsWith(PREFIX))
                .map(text -> FIELD.split(text, -1))
                .filter(fields -> fields.length >= FIELDS)
                .map(fields -> CpeCriteria.builder().part(fields[2]).vendor(fields[3]).product(fields[4])
                        .targetSoftware(fields[10]).build());
    }
}
