package com.vulntrend.batch.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("제품 이름 표준화 표")
class ProductAliasesTest {

    private static final ProductAlias CHROME_CPE = ProductAlias.of(ProductKey.of("google", "chrome"), "chrome", "Chrome");
    private static final ProductAlias CHROME_KEV =
            ProductAlias.of(ProductKey.of("Google", "Chromium V8"), "chrome", "Chrome");
    private static final ProductAlias PHP_CPE = ProductAlias.of(ProductKey.of("php", "php"), "php", "PHP");
    private static final ProductAlias PHP_KEV = ProductAlias.of(ProductKey.of("PHP", "PHP"), "php", "PHP");

    @Nested
    @DisplayName("표를 만들 때")
    class Building {

        @Test
        @DisplayName("CPE 와 KEV 이름이 맞춰 적어 같아지면 한 줄로 합친다")
        void sameKeySameProductIsMerged() {
            ProductAliases aliases = ProductAliases.of(List.of(PHP_CPE, PHP_KEV, CHROME_CPE));

            assertThat(aliases.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 이름을 서로 다른 제품으로 잇는 줄이 있으면 표를 받지 않는다")
        void sameKeyOtherProductIsRefused() {
            ProductAlias chromeAsEdge = ProductAlias.of(ProductKey.of("google", "chrome"), "edge", "Microsoft Edge");

            assertThatThrownBy(() -> ProductAliases.of(List.of(CHROME_CPE, chromeAsEdge)))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("google/chrome");
        }

        @Test
        @DisplayName("한 제품이 두 가지 이름으로 보이게 적혀 있으면 표를 받지 않는다")
        void oneSlugTwoDisplayNamesIsRefused() {
            ProductAlias chromeOtherName =
                    ProductAlias.of(ProductKey.of("Google", "Chromium"), "chrome", "Google Chrome");

            assertThatThrownBy(() -> ProductAliases.of(List.of(CHROME_CPE, chromeOtherName)))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("chrome");
        }

        @Test
        @DisplayName("묶인 제품 수를 센다")
        void countsProducts() {
            ProductAliases aliases = ProductAliases.of(List.of(CHROME_CPE, CHROME_KEV, PHP_CPE));

            assertThat(aliases.productCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("원래 이름으로 표준 이름을 찾을 때")
    class Finding {

        private final ProductAliases aliases = ProductAliases.of(List.of(CHROME_CPE, CHROME_KEV, PHP_CPE));

        @Test
        @DisplayName("KEV 자유 표기로 찾아도 같은 제품이 나온다")
        void findsByKevName() {
            assertThat(aliases.find(ProductKey.of("GOOGLE", "chromium v8")))
                    .map(ProductAlias::getDisplayName)
                    .contains("Chrome");
        }

        @Test
        @DisplayName("표에 없는 제품은 아무것도 나오지 않는다")
        void unknownIsEmpty() {
            assertThat(aliases.find(ProductKey.of("gimp", "gimp"))).isEmpty();
        }
    }
}
