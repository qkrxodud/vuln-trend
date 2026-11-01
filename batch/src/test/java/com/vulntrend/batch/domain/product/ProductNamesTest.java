package com.vulntrend.batch.domain.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("사이트에 보이는 제품 이름")
class ProductNamesTest {

    @Nested
    @DisplayName("CPE 제품 이름 원문을 사람이 읽게 바꿀 때")
    class Readable {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource(delimiter = '|', value = {
                "panorama_virtual_appliance | Panorama Virtual Appliance",
                "experience_manager | Experience Manager",
                "windows_10 | Windows 10"})
        @DisplayName("밑줄은 띄어 쓰고 단어 첫 글자를 대문자로 쓴다")
        void wordsAreCapitalized(String raw, String shown) {
            assertThat(ProductNames.readable(raw)).isEqualTo(shown);
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource(delimiter = '|', value = {
                "catalyst_sd-wan_manager | Catalyst SD-WAN Manager",
                "http_server | HTTP Server",
                "sql_server | SQL Server",
                "php_api_gateway | PHP API Gateway",
                "nx-os | NX-OS"})
        @DisplayName("알려진 약어는 대문자로 쓴다")
        void knownAbbreviationsAreUpperCase(String raw, String shown) {
            assertThat(ProductNames.readable(raw)).isEqualTo(shown);
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource(delimiter = '|', value = {
                "ruggedcom_ape1808_firmware | Ruggedcom APE1808 Firmware",
                "mdm9206_firmware | MDM9206 Firmware",
                "db2 | DB2"})
        @DisplayName("글자와 숫자가 섞인 모델명은 대문자로 쓴다")
        void modelNamesAreUpperCase(String raw, String shown) {
            assertThat(ProductNames.readable(raw)).isEqualTo(shown);
        }

        @Test
        @DisplayName("밑줄로 감싼 하이픈은 앞뒤를 띄운 줄표가 된다")
        void spacedHyphen() {
            assertThat(ProductNames.readable("smart_licensing_-_utility")).isEqualTo("Smart Licensing - Utility");
        }

        @Test
        @DisplayName("하이픈으로 이은 단어는 조각마다 첫 글자를 대문자로 쓴다")
        void hyphenatedWords() {
            assertThat(ProductNames.readable("e-business_suite")).isEqualTo("E-Business Suite");
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource(delimiter = '|', value = {
                "i/o_events | I/O Events",
                "scp:containment_breach | Scp:Containment Breach"})
        @DisplayName("빗금·쌍점으로 이은 단어도 조각마다 같은 규칙을 쓴다")
        void slashAndColonJoinPieces(String raw, String shown) {
            assertThat(ProductNames.readable(raw)).isEqualTo(shown);
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource(delimiter = '|', value = {
                "update_services_for_sap_solutions | Update Services for SAP Solutions",
                "for_developers | For Developers"})
        @DisplayName("짧은 이음말은 첫 단어가 아니면 소문자로 둔다")
        void smallWordsStayLowerCase(String raw, String shown) {
            assertThat(ProductNames.readable(raw)).isEqualTo(shown);
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource(delimiter = '|', value = {
                "node.js | Node.js",
                "joomla! | Joomla!"})
        @DisplayName("마침표와 문장 부호는 그대로 둔다")
        void punctuationStays(String raw, String shown) {
            assertThat(ProductNames.readable(raw)).isEqualTo(shown);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "Connect Secure",
                "PAN-OS",
                "macOS"})
        @DisplayName("사람이 적은 이름(대문자나 띄어쓰기가 있음)은 그대로 둔다")
        void writtenNamesStay(String written) {
            assertThat(ProductNames.readable(written)).isEqualTo(written);
        }
    }

    @Nested
    @DisplayName("저장된 제품 이름을 보일 때")
    class Shown {

        private final ProductNames names = ProductNames.of(List.of("curl", "Chrome"));

        @Test
        @DisplayName("표의 표시 이름은 소문자여도 표에 적힌 대로 보인다")
        void tableNameStays() {
            assertThat(names.shown("curl")).isEqualTo("curl");
            assertThat(names.shown("Chrome")).isEqualTo("Chrome");
        }

        @Test
        @DisplayName("표에 없는 CPE 원문은 사람이 읽는 이름으로 보인다")
        void unmappedBecomesReadable() {
            assertThat(names.shown("panorama_virtual_appliance")).isEqualTo("Panorama Virtual Appliance");
        }

        @Test
        @DisplayName("제조사는 이름 앞에 붙이지 않는다")
        void noVendorPrefix() {
            assertThat(names.shown("ruggedcom_ape1808_firmware")).isEqualTo("Ruggedcom APE1808 Firmware");
        }
    }
}
