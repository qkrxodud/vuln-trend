package com.vulntrend.batch.domain.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("대표 제품 정하기")
class PrimaryProductRuleTest {

    private static final PrimaryProductRule RULE = PrimaryProductRule.of(ProductAliases.of(List.of(
            ProductAlias.of(ProductKey.of("google", "chrome"), "chrome", "Chrome"),
            ProductAlias.of(ProductKey.of("Google", "Chromium V8"), "chrome", "Chrome"),
            ProductAlias.of(ProductKey.of("microsoft", "edge_chromium"), "edge", "Microsoft Edge"),
            ProductAlias.of(ProductKey.of("apple", "iphone_os"), "ios", "iOS·iPadOS"))));

    private static final Optional<ProductMention> KEV_CHROMIUM = mention("Google", "Chromium V8");
    private static final Optional<ProductMention> KEV_APPLE_MANY = mention("Apple", "Multiple Products");
    private static final Optional<ProductMention> KEV_LANGFLOW = mention("Langflow", "Langflow");
    private static final Optional<ProductMention> CPE_EDGE = mention("microsoft", "edge_chromium");
    private static final Optional<ProductMention> CPE_IPHONE = mention("apple", "iphone_os");
    private static final Optional<ProductMention> CPE_LANGFLOW = mention("langflow", "langflow");
    private static final Optional<ProductMention> NONE = Optional.empty();

    @Nested
    @DisplayName("순위는 KEV 표기, 그다음 CPE 첫 항목이다")
    class Ranking {

        @Test
        @DisplayName("KEV 표기가 표에 있으면 CPE 첫 항목보다 앞선다")
        void kevFirst() {
            assertThat(RULE.choose(KEV_CHROMIUM, CPE_EDGE)).contains("Chrome");
        }

        @Test
        @DisplayName("KEV 가 '여러 제품'으로 적었으면 CPE 첫 항목으로 넘어간다")
        void genericKevFallsThrough() {
            assertThat(RULE.choose(KEV_APPLE_MANY, CPE_IPHONE)).contains("iOS·iPadOS");
        }

        @Test
        @DisplayName("KEV 표기가 표에 없으면 표에 있는 CPE 첫 항목을 쓴다")
        void unmappedKevFallsToMappedCpe() {
            assertThat(RULE.choose(KEV_LANGFLOW, CPE_EDGE)).contains("Microsoft Edge");
        }

        @Test
        @DisplayName("KEV 에 없는 CVE 는 CPE 첫 항목의 표준 이름을 쓴다")
        void noKevUsesCpe() {
            assertThat(RULE.choose(NONE, CPE_EDGE)).contains("Microsoft Edge");
        }
    }

    @Nested
    @DisplayName("표에 없는 제품이면")
    class Unmapped {

        @Test
        @DisplayName("CPE 첫 항목의 제품 이름을 적힌 그대로 보인다")
        void showsCpeProductAsIs() {
            assertThat(RULE.choose(KEV_LANGFLOW, CPE_LANGFLOW)).contains("langflow");
            assertThat(RULE.choose(NONE, mention("nvidia", "nemo_megatron_bridge"))).contains("nemo_megatron_bridge");
        }

        @Test
        @DisplayName("NVD 가 아직 CPE 를 달지 않았으면 KEV 의 제품 이름을 적힌 그대로 보인다")
        void showsKevProductWhenNoCpe() {
            assertThat(RULE.choose(KEV_LANGFLOW, NONE)).contains("Langflow");
        }

        @Test
        @DisplayName("KEV 가 '여러 제품'으로만 적었고 CPE 도 없으면 대표 제품이 없다")
        void genericKevWithoutCpeIsEmpty() {
            assertThat(RULE.choose(KEV_APPLE_MANY, NONE)).isEmpty();
        }

        @Test
        @DisplayName("제품 정보가 아예 없으면 대표 제품이 없다")
        void nothingIsEmpty() {
            assertThat(RULE.choose(NONE, NONE)).isEmpty();
        }

        @Test
        @DisplayName("적힌 그대로의 이름이 100자를 넘으면 저장할 수 있게 100자로 자른다")
        void longRawNameIsCut() {
            String longName = "Secure Firewall Adaptive Security Appliance (ASA) and Secure Firewall Threat Defense (FTD) Software and Management Center";

            assertThat(RULE.choose(mention("Cisco", longName), NONE)).hasValueSatisfying(name ->
                    assertThat(name).hasSize(100).isEqualTo(longName.substring(0, 100)));
        }
    }

    @Nested
    @DisplayName("영향받는 제품의 묶음 이름은")
    class Slug {

        @Test
        @DisplayName("표에 있으면 표의 묶음 이름이 된다")
        void mappedHasSlug() {
            assertThat(RULE.slugOf(CPE_EDGE.orElseThrow())).contains("edge");
        }

        @Test
        @DisplayName("표에 없으면 묶음 이름이 없다")
        void unmappedHasNoSlug() {
            assertThat(RULE.slugOf(CPE_LANGFLOW.orElseThrow())).isEmpty();
        }
    }

    private static Optional<ProductMention> mention(String vendor, String product) {
        return ProductMention.from(vendor, product);
    }
}
