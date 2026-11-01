package com.vulntrend.batch.domain.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("제품군 정하기 — 확실한 규칙만, 모르면 분류 전")
class ProductFamilyRuleTest {

    private static final String NO_CNA = "";

    private static Optional<ProductFamily> classify(String cpe, String cna) {
        return ProductFamilyRule.classify(Optional.empty(), CpeCriteria.parse(cpe), cna);
    }

    private static String cpe(String part, String vendor, String product, String targetSoftware) {
        return "cpe:2.3:" + part + ":" + vendor + ":" + product + ":*:*:*:*:*:" + targetSoftware + ":*:*";
    }

    @Test
    @DisplayName("대표 제품이 표에 있으면 표의 제품군이 다른 근거보다 먼저다")
    void tableFirst() {
        assertThat(ProductFamilyRule.classify(Optional.of(ProductFamily.NETWORK),
                CpeCriteria.parse(cpe("o", "linux", "linux_kernel", "*")), "security@wordfence.com"))
                .contains(ProductFamily.NETWORK);
    }

    @ParameterizedTest(name = "대상 {0} → {1}")
    @CsvSource({"wordpress, WEB_CMS", "drupal, WEB_CMS", "joomla\\!, WEB_CMS", "node.js, DEV", "python, DEV",
            "jenkins, DEV", "android, MOBILE", "iphone_os, MOBILE"})
    @DisplayName("첫 CPE 의 대상 소프트웨어가 정해진 것이면 그 제품군이다")
    void targetSoftware(String target, ProductFamily family) {
        assertThat(classify(cpe("a", "someone", "some_plugin", target), NO_CNA)).contains(family);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"audit@patchstack.com, WEB_CMS", "security@wordfence.com, WEB_CMS", "contact@wpscan.com, WEB_CMS",
            "mlhess@drupal.org, WEB_CMS", "416baaa9-dc9f-4396-8d5f-8c081fb06d67, OS",
            "chrome-cve-admin@google.com, BROWSER", "ics-cert@hq.dhs.gov, ICS_IOT",
            "vulnerabilities@zephyrproject.org, ICS_IOT"})
    @DisplayName("CPE 가 아직 없어도 다루는 제품이 하나로 정해진 CNA 가 발행했으면 그 제품군이다")
    void cna(String cna, ProductFamily family) {
        assertThat(classify(null, cna)).contains(family);
    }

    @Nested
    @DisplayName("첫 CPE 의 종류로는")
    class Part {

        @Test
        @DisplayName("장비와 펌웨어는 산업제어·IoT·펌웨어다")
        void hardwareAndFirmware() {
            assertThat(classify(cpe("h", "siemens", "simatic_s7-1500", "*"), NO_CNA)).contains(ProductFamily.ICS_IOT);
            assertThat(classify(cpe("o", "dlink", "dir-513_firmware", "*"), NO_CNA)).contains(ProductFamily.ICS_IOT);
        }

        @ParameterizedTest(name = "{0}:{1} → {2}")
        @CsvSource({"linux, linux_kernel, OS", "microsoft, windows_10_1607, OS", "microsoft, windows_server_2022, OS",
                "apple, macos, OS", "debian, debian_linux, OS", "redhat, enterprise_linux_server, OS",
                "freebsd, freebsd, OS", "google, android, MOBILE", "apple, iphone_os, MOBILE",
                "huawei, harmonyos, MOBILE", "xen, xen, CLOUD"})
        @DisplayName("알려진 범용 운영체제·모바일 운영체제·하이퍼바이저만 그 제품군이다")
        void knownOperatingSystems(String vendor, String product, ProductFamily family) {
            assertThat(classify(cpe("o", vendor, product, "*"), NO_CNA)).contains(family);
        }

        @ParameterizedTest(name = "{0}:{1}")
        @CsvSource({"fortinet, fortios", "paloaltonetworks, pan-os", "cisco, ios_xe", "juniper, junos",
                "mikrotik, routeros", "qnap, qts", "microsoft, windows_phone"})
        @DisplayName("네트워크 장비 OS 나 NAS OS 처럼 범용 운영체제가 아닌 것은 분류 전이다")
        void otherOperatingSystemsStayUnclassified(String vendor, String product) {
            assertThat(classify(cpe("o", vendor, product, "*"), NO_CNA)).isEmpty();
        }
    }

    @Test
    @DisplayName("근거가 하나도 맞지 않으면 분류 전이다 — 틀린 분류보다 낫다")
    void nothingMatches() {
        assertThat(classify(cpe("a", "acme", "widget", "*"), "security-advisories@github.com")).isEmpty();
        assertThat(classify(null, null)).isEmpty();
    }
}
