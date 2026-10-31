package com.vulntrend.batch.domain.product;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 제품 이름 표준화 표를 찾는 열쇠(product_alias 의 cpe_vendor·cpe_product).
 * NVD CPE 표기("paloaltonetworks"/"pan-os")와 KEV 자유 표기("Palo Alto Networks"/"PAN-OS")를 같은 규칙으로 맞춰 적는다 —
 * 소문자로 바꾸고, 영문·숫자가 아닌 글자가 이어지면 밑줄 하나로, 앞뒤 밑줄은 뗀다(리스크 R7 혼합안, 2026-10-31 확정).
 * 적재와 조회가 모두 이 열쇠를 거쳐야 같은 이름이 같은 줄을 찾는다.
 */
@Getter
@EqualsAndHashCode
public class ProductKey {

    /** product_alias 의 cpe_vendor·cpe_product 는 varchar(100) */
    public static final int MAX_LENGTH = 100;

    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_UNDERSCORES = Pattern.compile("^_+|_+$");

    private final String vendor;
    private final String product;

    @Builder(access = AccessLevel.PRIVATE)
    private ProductKey(String vendor, String product) {
        validate(vendor, "vendor");
        validate(product, "product");
        this.vendor = vendor;
        this.product = product;
    }

    public static ProductKey of(String rawVendor, String rawProduct) {
        return ProductKey.builder()
                .vendor(normalize(rawVendor))
                .product(normalize(rawProduct))
                .build();
    }

    private static String normalize(String raw) {
        if (Objects.isNull(raw)) {
            return "";
        }
        String folded = NOT_LETTER_OR_DIGIT.matcher(raw.toLowerCase(Locale.ROOT)).replaceAll("_");
        return EDGE_UNDERSCORES.matcher(folded).replaceAll("");
    }

    private static void validate(String normalized, String part) {
        if (normalized.isEmpty()) {
            throw new DomainValidationException("A product " + part + " name must contain a letter or a digit.");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new DomainValidationException("A product " + part + " name must be at most " + MAX_LENGTH + " characters.");
        }
    }

    @Override
    public String toString() {
        return vendor + "/" + product;
    }
}
