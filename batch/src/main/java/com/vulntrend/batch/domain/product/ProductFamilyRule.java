package com.vulntrend.batch.domain.product;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 제품군 정하기(사용자 결정 2026-11-01). 틀린 분류보다 분류 전(빈 값)이 낫다 — 확실한 규칙만, 먼저 맞는 것을 쓴다.
 * 1) 대표 제품이 표(product-alias.yml)에 있으면 표의 제품군
 * 2) 첫 CPE 의 대상 소프트웨어: wordpress·drupal·joomla! → 웹 CMS, node.js·python·rust·go·ruby·jenkins → 개발, android·iphone_os → 모바일
 * 3) 다루는 제품이 하나로 정해진 CNA: Patchstack·Wordfence·WPScan·Drupal → 웹 CMS, kernel.org → 운영체제, Chrome → 브라우저,
 *    CISA ICS·Zephyr → 산업제어·IoT (2026-11-01 실측: Patchstack 6,227건 전부 WordPress 링크, kernel.org 9,360건 전부 "Linux kernel" 등)
 * 4) 첫 CPE 의 종류: 장비(h)·펌웨어(o, *_firmware) → 산업제어·IoT·펌웨어. 운영체제(o)는 알려진 범용 OS·모바일 OS·하이퍼바이저만
 *    (네트워크 장비 OS·NAS OS 는 분류 전 — 오케스트레이터 확정 2026-11-01)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductFamilyRule {

    private static final String HARDWARE = "h";
    private static final String OPERATING_SYSTEM = "o";
    private static final String FIRMWARE = "_firmware";
    private static final Map<String, ProductFamily> TARGET_SOFTWARE = Map.ofEntries(
            Map.entry("wordpress", ProductFamily.WEB_CMS), Map.entry("drupal", ProductFamily.WEB_CMS),
            Map.entry("joomla\\!", ProductFamily.WEB_CMS), Map.entry("node.js", ProductFamily.DEV),
            Map.entry("python", ProductFamily.DEV), Map.entry("rust", ProductFamily.DEV), Map.entry("go", ProductFamily.DEV),
            Map.entry("ruby", ProductFamily.DEV), Map.entry("jenkins", ProductFamily.DEV),
            Map.entry("android", ProductFamily.MOBILE), Map.entry("iphone_os", ProductFamily.MOBILE));
    private static final Map<String, ProductFamily> CNA = Map.of(
            "audit@patchstack.com", ProductFamily.WEB_CMS,
            "security@wordfence.com", ProductFamily.WEB_CMS,
            "contact@wpscan.com", ProductFamily.WEB_CMS,
            "mlhess@drupal.org", ProductFamily.WEB_CMS,
            "416baaa9-dc9f-4396-8d5f-8c081fb06d67", ProductFamily.OS,
            "chrome-cve-admin@google.com", ProductFamily.BROWSER,
            "ics-cert@hq.dhs.gov", ProductFamily.ICS_IOT,
            "vulnerabilities@zephyrproject.org", ProductFamily.ICS_IOT);
    private static final Set<String> GENERAL_OS = Set.of("linux:linux_kernel", "apple:mac_os_x", "apple:macos",
            "apple:mac_os_x_server", "apple:mac_os", "debian:debian_linux", "canonical:ubuntu_linux", "redhat:linux",
            "fedoraproject:fedora", "opensuse:opensuse", "opensuse:leap", "opensuse:tumbleweed", "suse:suse_linux",
            "suse:linux_enterprise_server", "suse:linux_enterprise_desktop", "oracle:linux", "centos:centos",
            "freebsd:freebsd", "openbsd:openbsd", "netbsd:netbsd", "sun:solaris", "oracle:solaris", "sun:sunos",
            "sun:opensolaris", "ibm:aix", "ibm:i", "hp:hp-ux", "sgi:irix", "google:chrome_os");
    private static final Set<String> MOBILE_OS = Set.of("google:android", "samsung:android", "apple:iphone_os",
            "apple:ipados", "apple:ipad_os", "huawei:emui", "huawei:harmonyos");
    private static final Set<String> HYPERVISOR = Set.of("xen:xen");
    private static final Pattern WINDOWS = Pattern.compile("windows(_(?!phone|mobile|ce\\b)[a-z0-9_.-]+)?");
    private static final Pattern RED_HAT_ENTERPRISE = Pattern.compile("enterprise_linux(_[a-z0-9_]+)?");

    public static Optional<ProductFamily> classify(Optional<ProductFamily> tableFamily, Optional<CpeCriteria> firstCpe,
            String cna) {
        return tableFamily
                .or(() -> firstCpe.flatMap(ProductFamilyRule::byTargetSoftware))
                .or(() -> Optional.ofNullable(cna).map(CNA::get))
                .or(() -> firstCpe.flatMap(ProductFamilyRule::byPart));
    }

    private static Optional<ProductFamily> byTargetSoftware(CpeCriteria cpe) {
        return Optional.ofNullable(TARGET_SOFTWARE.get(cpe.getTargetSoftware()));
    }

    private static Optional<ProductFamily> byPart(CpeCriteria cpe) {
        if (HARDWARE.equals(cpe.getPart())
                || OPERATING_SYSTEM.equals(cpe.getPart()) && cpe.getProduct().endsWith(FIRMWARE)) {
            return Optional.of(ProductFamily.ICS_IOT);
        }
        return OPERATING_SYSTEM.equals(cpe.getPart()) ? operatingSystem(cpe) : Optional.empty();
    }

    private static Optional<ProductFamily> operatingSystem(CpeCriteria cpe) {
        String name = cpe.getVendor() + ":" + cpe.getProduct();
        if (GENERAL_OS.contains(name) || isWindows(cpe) || isRedHatEnterprise(cpe)) {
            return Optional.of(ProductFamily.OS);
        }
        if (MOBILE_OS.contains(name)) {
            return Optional.of(ProductFamily.MOBILE);
        }
        return HYPERVISOR.contains(name) ? Optional.of(ProductFamily.CLOUD) : Optional.empty();
    }

    private static boolean isWindows(CpeCriteria cpe) {
        return "microsoft".equals(cpe.getVendor()) && WINDOWS.matcher(cpe.getProduct()).matches();
    }

    private static boolean isRedHatEnterprise(CpeCriteria cpe) {
        return "redhat".equals(cpe.getVendor()) && RED_HAT_ENTERPRISE.matcher(cpe.getProduct()).matches();
    }
}
