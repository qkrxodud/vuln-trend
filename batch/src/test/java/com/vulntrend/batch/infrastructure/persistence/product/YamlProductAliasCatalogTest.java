package com.vulntrend.batch.infrastructure.persistence.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.product.ProductAlias;
import com.vulntrend.batch.domain.product.ProductAliases;
import com.vulntrend.batch.domain.product.ProductFamily;
import com.vulntrend.batch.domain.product.ProductKey;
import com.vulntrend.batch.global.error.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

@DisplayName("제품 이름 표준화 표 원본(product-alias.yml)")
class YamlProductAliasCatalogTest {

    private final YamlProductAliasCatalog catalog = new YamlProductAliasCatalog(new ObjectMapper());

    @Nested
    @DisplayName("저장소에 들어 있는 원본을 읽으면")
    class RealFile {

        private final ProductAliases aliases = catalog.load();

        @Test
        @DisplayName("초기 제품 50개와 Gate G 에서 더한 40개, 제품 90개가 모두 들어 있다")
        void hasNinetyProducts() {
            assertThat(aliases.productCount()).isEqualTo(90);
        }

        @Test
        @DisplayName("애매한 두 제품(SiteGuard, Sangoma Switchvox)만 빼고 모든 제품에 제품군이 있다")
        void everyProductHasFamilyButTwo() {
            assertThat(aliases.getAliases().stream().filter(alias -> alias.getFamily().isEmpty())
                    .map(ProductAlias::getDisplayName).distinct())
                    .containsExactlyInAnyOrder("SiteGuard", "Sangoma Switchvox");
        }

        @ParameterizedTest(name = "{0} / {1} → {2}")
        @CsvSource(delimiter = '|', value = {
                "microsoft      | windows_10_1607 | OS",
                "google         | chrome          | BROWSER",
                "Fortinet       | FortiOS         | NETWORK",
                "wordpress      | wordpress       | WEB_CMS",
                "apache         | tomcat          | SERVER",
                "apache         | log4j           | DEV",
                "vmware         | esxi            | CLOUD",
                "google         | android         | MOBILE",
                "rarlab         | winrar          | DESKTOP"})
        @DisplayName("제품마다 정한 제품군을 읽는다")
        void familyIsRead(String vendor, String product, ProductFamily family) {
            assertThat(aliases.find(ProductKey.of(vendor, product)).flatMap(ProductAlias::getFamily)).contains(family);
        }

        @ParameterizedTest(name = "{0} / {1} → {3}")
        @CsvSource(delimiter = '|', value = {
                "microsoft          | windows_10_1607 | windows        | Windows",
                "microsoft          | windows_server_2025 | windows    | Windows",
                "Microsoft          | Win32k          | windows        | Windows",
                "google             | chrome          | chrome         | Chrome",
                "Google             | Chromium V8     | chrome         | Chrome",
                "paloaltonetworks   | pan-os          | pan-os         | Palo Alto PAN-OS",
                "Palo Alto Networks | PAN-OS          | pan-os         | Palo Alto PAN-OS",
                "microsoft          | .net_framework  | dotnet         | .NET",
                "apple              | ipados          | ios            | iOS·iPadOS",
                "jfrog              | artifactory     | jfrog-artifactory | JFrog Artifactory",
                "Plex               | Media Server    | plex-media-server | Plex Media Server",
                "mikrotik           | routeros        | routeros       | MikroTik RouterOS",
                "Collabora          | Collabora Online – Built-in CODE Server | collabora-online | Collabora Online",
                "oracle             | http_server     | oracle-http-server | Oracle HTTP Server",
                "apache             | http_server     | apache-http-server | Apache HTTP Server",
                "microsoft          | sql_server      | sql-server     | Microsoft SQL Server",
                "microsoft          | sql_server_2016 | sql-server     | Microsoft SQL Server",
                "microsoft          | sql_server_2017 | sql-server     | Microsoft SQL Server",
                "michaelschwarz     | ajax.net_professional | ajax-net-professional | Ajax.NET Professional",
                "f5                 | nginx           | nginx          | nginx",
                "WordPress          | Core            | wordpress      | WordPress"})
        @DisplayName("CPE 표기와 KEV 표기가 같은 표준 이름으로 이어진다")
        void namesMeet(String vendor, String product, String slug, String displayName) {
            ProductAlias alias = aliases.find(ProductKey.of(vendor, product)).orElseThrow();

            assertThat(alias.getSlug()).isEqualTo(slug);
            assertThat(alias.getDisplayName()).isEqualTo(displayName);
        }

        @Test
        @DisplayName("KEV 의 여러 제품 묶음 표기는 표에 없어 CPE 첫 항목으로 넘어간다")
        void multipleProductsIsNotMapped() {
            assertThat(aliases.find(ProductKey.of("Apple", "Multiple Products"))).isEmpty();
        }

        @Test
        @DisplayName("WordPress 플러그인은 WordPress 본체로 이어지지 않는다")
        void wordpressPluginIsNotCore() {
            assertThat(aliases.find(ProductKey.of("WordPress", "File Manager Plugin"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("원본이 잘못 적혀 있으면")
    class BrokenFile {

        @Test
        @DisplayName("모르는 제품군 키를 적으면 표를 받지 않는다")
        void unknownFamilyIsRefused() {
            assertThatThrownBy(() -> catalog.read(new ClassPathResource("product-alias/unknown-family.yml")))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("browsers");
        }

        @Test
        @DisplayName("모르는 항목 이름(오타)이 있으면 읽지 않는다")
        void unknownFieldIsRefused() {
            assertThatThrownBy(() -> catalog.read(new ClassPathResource("product-alias/unknown-field.yml")))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("원래 이름이 하나도 없는 제품이 있으면 읽지 않는다")
        void productWithoutNamesIsRefused() {
            assertThatThrownBy(() -> catalog.read(new ClassPathResource("product-alias/no-alias.yml")))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("chrome");
        }

        @Test
        @DisplayName("같은 원래 이름을 두 제품에 이었으면 읽지 않는다")
        void conflictIsRefused() {
            assertThatThrownBy(() -> catalog.read(new ClassPathResource("product-alias/conflict.yml")))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("google/chrome");
        }

        @Test
        @DisplayName("YAML 문법이 깨졌으면 읽지 않는다")
        void notYamlIsRefused() {
            assertThatThrownBy(() -> catalog.read(new ClassPathResource("product-alias/not-yaml.yml")))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("원본 파일이 없으면 읽지 않는다")
        void missingFileIsRefused() {
            assertThatThrownBy(() -> catalog.read(new ClassPathResource("product-alias/absent.yml")))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("제품 목록이 비어 있으면 읽지 않는다")
        void emptyFileIsRefused() {
            assertThatThrownBy(() -> catalog.read(new ClassPathResource("product-alias/empty.yml")))
                    .isInstanceOf(DomainValidationException.class);
        }
    }
}
