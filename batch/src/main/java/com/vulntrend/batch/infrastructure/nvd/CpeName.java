package com.vulntrend.batch.infrastructure.nvd;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * CPE 2.3 이름(cpe:2.3:part:vendor:product:version:update:…)에서 제조사·제품·버전·업데이트를 읽는다.
 * 역슬래시로 감싼 글자(\: \& \( 등)는 칸 구분으로 보지 않고 원래 글자로 되돌린다. * 와 - 는 "정해진 값 없음".
 */
public class CpeName {

    private static final String PREFIX = "cpe:2.3:";
    private static final int VENDOR = 3;
    private static final int PRODUCT = 4;
    private static final int VERSION = 5;
    private static final int UPDATE = 6;
    private static final Set<String> ANY = Set.of("*", "-");

    @Getter
    private final String vendor;
    @Getter
    private final String product;
    private final String version;
    private final String update;

    @Builder(access = AccessLevel.PRIVATE)
    private CpeName(String vendor, String product, String version, String update) {
        this.vendor = vendor;
        this.product = product;
        this.version = version;
        this.update = update;
    }

    public static Optional<CpeName> parse(String criteria) {
        if (Objects.isNull(criteria) || isNotCpe23(criteria)) {
            return Optional.empty();
        }
        List<String> fields = split(criteria);
        if (fields.size() <= UPDATE) {
            return Optional.empty();
        }
        return Optional.of(CpeName.builder()
                .vendor(fields.get(VENDOR))
                .product(fields.get(PRODUCT))
                .version(fields.get(VERSION))
                .update(fields.get(UPDATE))
                .build());
    }

    public Optional<String> getVersion() {
        return specific(version);
    }

    public Optional<String> getUpdate() {
        return specific(update);
    }

    private static boolean isNotCpe23(String criteria) {
        return Predicate.not(CpeName::isCpe23).test(criteria);
    }

    private static boolean isCpe23(String criteria) {
        return criteria.startsWith(PREFIX);
    }

    private static Optional<String> specific(String value) {
        return ANY.contains(value) ? Optional.empty() : Optional.of(value);
    }

    private static List<String> split(String criteria) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (char c : criteria.toCharArray()) {
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == ':') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
