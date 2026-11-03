package com.vulntrend.batch.domain.cvelist;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * CVE.org cvelistV5 레코드의 containers.cna.affected[] 한 칸(S3a). CNA 가 적은 그대로 담는다 — 대표 제품·제품군 선정은 3b.
 * vendor·product 또는 packageName 중 하나는 있다(CVE JSON 5 규칙). cpes·packageURL 은 CNA 가 줄 때만 있다.
 */
@EqualsAndHashCode
public class CnaAffected {

    private final String vendor;
    private final String product;
    private final String packageName;
    private final String defaultStatus;
    @Getter
    private final List<CnaVersion> versions;
    @Getter
    private final List<String> cpes;
    private final String packageUrl;

    @Builder(access = AccessLevel.PRIVATE)
    private CnaAffected(String vendor, String product, String packageName, String defaultStatus,
            List<CnaVersion> versions, List<String> cpes, String packageUrl) {
        this.vendor = vendor;
        this.product = product;
        this.packageName = packageName;
        this.defaultStatus = defaultStatus;
        this.versions = List.copyOf(versions);
        this.cpes = List.copyOf(cpes);
        this.packageUrl = packageUrl;
    }

    /** 없는 이름·상태·packageURL 은 null, 없는 목록은 빈 목록으로 받는다 */
    public static CnaAffected of(String vendor, String product, String packageName, String defaultStatus,
            List<CnaVersion> versions, List<String> cpes, String packageUrl) {
        return CnaAffected.builder().vendor(vendor).product(product).packageName(packageName)
                .defaultStatus(defaultStatus).versions(versions).cpes(cpes).packageUrl(packageUrl).build();
    }

    public Optional<String> getVendor() {
        return Optional.ofNullable(vendor);
    }

    public Optional<String> getProduct() {
        return Optional.ofNullable(product);
    }

    public Optional<String> getPackageName() {
        return Optional.ofNullable(packageName);
    }

    public Optional<String> getDefaultStatus() {
        return Optional.ofNullable(defaultStatus);
    }

    public Optional<String> getPackageUrl() {
        return Optional.ofNullable(packageUrl);
    }
}
