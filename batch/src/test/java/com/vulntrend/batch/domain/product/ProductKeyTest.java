package com.vulntrend.batch.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("제품 찾기 열쇠")
class ProductKeyTest {

    @Nested
    @DisplayName("제조사와 제품 이름을 맞춰 적을 때")
    class Normalizing {

        @Test
        @DisplayName("NVD 의 CPE 표기는 그대로 소문자로 남는다")
        void cpeStaysAsIs() {
            ProductKey key = ProductKey.of("google", "chrome");

            assertThat(key.getVendor()).isEqualTo("google");
            assertThat(key.getProduct()).isEqualTo("chrome");
        }

        @Test
        @DisplayName("KEV 의 자유 표기는 대문자를 소문자로, 공백과 기호를 밑줄 하나로 바꾼다")
        void kevIsFolded() {
            ProductKey key = ProductKey.of("Palo Alto Networks", "PAN-OS");

            assertThat(key.getVendor()).isEqualTo("palo_alto_networks");
            assertThat(key.getProduct()).isEqualTo("pan_os");
        }

        @Test
        @DisplayName("이어진 기호는 밑줄 하나가 되고 앞뒤 기호는 떨어진다")
        void runsAndEdgesAreTrimmed() {
            ProductKey key = ProductKey.of(" Microsoft ", ".NET Framework");

            assertThat(key.getVendor()).isEqualTo("microsoft");
            assertThat(key.getProduct()).isEqualTo("net_framework");
        }

        @Test
        @DisplayName("CPE 와 KEV 가 같은 제품을 다르게 적어도 같은 열쇠가 된다")
        void cpeAndKevMeet() {
            assertThat(ProductKey.of("PHP", "PHP")).isEqualTo(ProductKey.of("php", "php"));
            assertThat(ProductKey.of("Cisco", "IOS software")).isEqualTo(ProductKey.of("Cisco", "IOS Software"));
        }

        @Test
        @DisplayName("CPE 의 escape 된 빗금도 밑줄로 바뀐다")
        void escapedSlashIsFolded() {
            assertThat(ProductKey.of("intel", "proset\\/wireless_wifi").getProduct())
                    .isEqualTo("proset_wireless_wifi");
        }
    }

    @Nested
    @DisplayName("열쇠로 받을 수 없는 이름")
    class Refusing {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.vulntrend.batch.domain.product.ProductKeyTest#emptyNames")
        @DisplayName("제조사나 제품 이름이 비어 있으면 열쇠를 만들지 않는다")
        void emptyIsRefused(String[] names) {
            assertThatThrownBy(() -> ProductKey.of(names[0], names[1]))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("맞춰 적은 이름이 100자를 넘으면 저장할 수 없어 받지 않는다")
        void tooLongIsRefused() {
            String longName = "a".repeat(101);

            assertThatThrownBy(() -> ProductKey.of("google", longName))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    static Stream<Named<String[]>> emptyNames() {
        return Stream.of(
                named("제조사 값이 없을 때", new String[] {null, "chrome"}),
                named("제품 값이 없을 때", new String[] {"google", null}),
                named("공백만 있을 때", new String[] {"google", "   "}),
                named("기호만 있을 때", new String[] {"google", "--"}));
    }
}
