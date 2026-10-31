package com.vulntrend.batch.domain.enrichment;

import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 실제 악용 목록(CISA KEV)의 한 건. vulnerability 의 is_kev·kev_added_at·kev_due_date·kev_ransomware 를 채우고,
 * 원본(payload)은 vulnerability_raw(source=kev)에 그대로 남긴다 — 대표 제품 선정(WBS 2.3)이 vendorProject·product 를 거기서 읽는다.
 */
public class KevEntry {

    /** CISA knownRansomwareCampaignUse 값 중 랜섬웨어 사용을 뜻하는 것. 나머지는 "Unknown" */
    private static final String RANSOMWARE_KNOWN = "known";

    @Getter
    private final CveId cveId;
    @Getter
    private final LocalDate dateAdded;
    private final LocalDate dueDate;
    @Getter
    private final boolean ransomware;
    @Getter
    private final String payload;

    @Builder(access = AccessLevel.PRIVATE)
    private KevEntry(CveId cveId, LocalDate dateAdded, LocalDate dueDate, boolean ransomware, String payload) {
        this.cveId = cveId;
        this.dateAdded = dateAdded;
        this.dueDate = dueDate;
        this.ransomware = ransomware;
        this.payload = payload;
    }

    public static KevEntry of(CveId cveId, LocalDate dateAdded, LocalDate dueDate, String ransomwareUse, String payload) {
        validate(cveId, dateAdded, payload);
        return KevEntry.builder()
                .cveId(cveId)
                .dateAdded(dateAdded)
                .dueDate(dueDate)
                .ransomware(isKnownRansomwareUse(ransomwareUse))
                .payload(payload)
                .build();
    }

    public Optional<LocalDate> getDueDate() {
        return Optional.ofNullable(dueDate);
    }

    private static boolean isKnownRansomwareUse(String use) {
        return Objects.nonNull(use) && RANSOMWARE_KNOWN.equals(use.trim().toLowerCase(Locale.ROOT));
    }

    private static void validate(CveId cveId, LocalDate dateAdded, String payload) {
        if (Objects.isNull(cveId)) {
            throw new DomainValidationException("A KEV entry needs a CVE id.");
        }
        if (Objects.isNull(dateAdded)) {
            throw new DomainValidationException("A KEV entry needs the date it was added to the catalog.");
        }
        if (Objects.isNull(payload) || payload.isBlank()) {
            throw new DomainValidationException("A KEV entry needs the original payload.");
        }
    }
}
