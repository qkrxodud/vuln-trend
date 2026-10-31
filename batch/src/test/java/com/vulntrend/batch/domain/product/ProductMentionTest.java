package com.vulntrend.batch.domain.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("원래 적힌 제품 이름")
class ProductMentionTest {

    @Test
    @DisplayName("제조사와 제품 이름을 적힌 그대로 담고 표 찾기 열쇠로 맞춰 적을 수 있다")
    void keepsRawAndFolds() {
        ProductMention mention = ProductMention.from("Palo Alto Networks", "PAN-OS").orElseThrow();

        assertThat(mention.getVendor()).isEqualTo("Palo Alto Networks");
        assertThat(mention.getProduct()).isEqualTo("PAN-OS");
        assertThat(mention.key()).contains(ProductKey.of("palo alto networks", "pan os"));
    }

    @Test
    @DisplayName("제조사나 제품 이름이 비어 있으면 이름이 없는 것으로 본다")
    void blankIsAbsent() {
        assertThat(ProductMention.from("Apple", " ")).isEmpty();
        assertThat(ProductMention.from(null, "iOS")).isEmpty();
        assertThat(ProductMention.from("Apple", "--")).isEmpty();
    }

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource(delimiter = '|', value = {
            "Apple     | Multiple Products",
            "Ivanti    | MobileIron Multiple Products",
            "Zyxel     | Multiple Firewalls",
            "Qualcomm  | Multiple Chipsets"})
    @DisplayName("KEV 의 '여러 제품' 묶음 표기는 한 제품을 가리키지 않는다")
    void multipleIsGeneric(String vendor, String product) {
        assertThat(ProductMention.from(vendor, product).orElseThrow().isGeneric()).isTrue();
    }

    @Test
    @DisplayName("한 제품 이름은 묶음 표기가 아니다")
    void singleProductIsNotGeneric() {
        assertThat(ProductMention.from("Apache", "Log4j2").orElseThrow().isGeneric()).isFalse();
    }
}
