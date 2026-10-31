package com.vulntrend.batch.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("제품 이름 표준화 표의 한 줄")
class ProductAliasTest {

    private static final ProductKey CHROMIUM_V8 = ProductKey.of("Google", "Chromium V8");

    @Test
    @DisplayName("원래 이름을 사이트에 보일 표준 이름과 묶음 이름으로 잇는다")
    void linksNameToStandardName() {
        ProductAlias alias = ProductAlias.of(CHROMIUM_V8, "chrome", "Chrome");

        assertThat(alias.getKey()).isEqualTo(CHROMIUM_V8);
        assertThat(alias.getSlug()).isEqualTo("chrome");
        assertThat(alias.getDisplayName()).isEqualTo("Chrome");
    }

    @Test
    @DisplayName("사이트에 보일 이름의 앞뒤 공백은 떨어진다")
    void displayNameIsTrimmed() {
        assertThat(ProductAlias.of(CHROMIUM_V8, "chrome", "  Chrome ").getDisplayName()).isEqualTo("Chrome");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"Chrome", "apache http", "-chrome", "chrome-", "pan--os", ""})
    @DisplayName("묶음 이름은 소문자·숫자와 사이의 하이픈만 쓸 수 있다")
    void badSlugIsRefused(String slug) {
        assertThatThrownBy(() -> ProductAlias.of(CHROMIUM_V8, slug, "Chrome"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("사이트에 보일 이름이 비어 있으면 받지 않는다")
    void blankDisplayNameIsRefused() {
        assertThatThrownBy(() -> ProductAlias.of(CHROMIUM_V8, "chrome", " "))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> ProductAlias.of(CHROMIUM_V8, "chrome", null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("묶음 이름이나 사이트에 보일 이름이 100자를 넘으면 저장할 수 없어 받지 않는다")
    void tooLongIsRefused() {
        assertThatThrownBy(() -> ProductAlias.of(CHROMIUM_V8, "c".repeat(101), "Chrome"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> ProductAlias.of(CHROMIUM_V8, "chrome", "C".repeat(101)))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("찾기 열쇠가 없으면 받지 않는다")
    void missingKeyIsRefused() {
        assertThatThrownBy(() -> ProductAlias.of(null, "chrome", "Chrome"))
                .isInstanceOf(DomainValidationException.class);
    }
}
