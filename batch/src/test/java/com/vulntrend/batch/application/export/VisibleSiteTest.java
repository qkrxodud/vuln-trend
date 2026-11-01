package com.vulntrend.batch.application.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.product.ProductNames;
import com.vulntrend.batch.domain.stat.KevAddedWeeks;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("게시할지 정하는 화면 지문")
class VisibleSiteTest {

    private static final ProductNames NAMES = ProductNames.of(List.of());
    private static final KevAddedWeeks WEEKS = KevAddedWeeks.of(9, 8);
    private static final Map<String, List<String>> TOPS = Map.of("product_family:browser",
            List.of("CVE-2026-41203", "CVE-2026-41204"));

    private static TrendRowJson kev() {
        return TrendRows.from(TrendListings.kev("CVE-2026-41203"), NAMES);
    }

    private static TrendRowJson unrated() {
        return TrendRows.from(TrendListings.unrated("CVE-2026-50001"), NAMES);
    }

    private static String fingerprint(TrendRowJson... rows) {
        return VisibleSite.fingerprint(List.of(rows), WEEKS, TOPS);
    }

    @Nested
    @DisplayName("화면에 보이는 값이 바뀌면 지문이 바뀐다")
    class Changes {

        private final String base = fingerprint(kev(), unrated());

        @Test
        @DisplayName("주목 목록의 순서")
        void order() {
            assertThat(fingerprint(unrated(), kev())).isNotEqualTo(base);
        }

        @Test
        @DisplayName("정수 점수와 심각도")
        void scoreAndSeverity() {
            assertThat(fingerprint(kevWith("score", 66), unrated())).isNotEqualTo(base);
            assertThat(fingerprint(kevWith("severity", "HIGH"), unrated())).isNotEqualTo(base);
        }

        @Test
        @DisplayName("KEV 여부·조치 기한·랜섬웨어·등재일")
        void kevValues() {
            assertThat(fingerprint(kevWith("kev", false), unrated())).isNotEqualTo(base);
            assertThat(fingerprint(kevWith("due", LocalDate.parse("2026-11-04")), unrated()))
                    .isNotEqualTo(base);
            assertThat(fingerprint(kevWith("ransomware", true), unrated())).isNotEqualTo(base);
            assertThat(fingerprint(kevWith("added", LocalDate.parse("2026-10-20")), unrated()))
                    .isNotEqualTo(base);
        }

        @Test
        @DisplayName("한국어 요약이 생기거나 없어짐")
        void summaryAppears() {
            assertThat(fingerprint(kevWith("summary", null), unrated())).isNotEqualTo(base);
        }

        @Test
        @DisplayName("업데이트할 버전과 패치 링크")
        void remedy() {
            assertThat(fingerprint(kevWith("fix", List.of("130.0.6723.60")), unrated()))
                    .isNotEqualTo(base);
            assertThat(fingerprint(kevWith("patch", null), unrated())).isNotEqualTo(base);
        }

        @Test
        @DisplayName("행의 제목과 보이는 제품 이름")
        void titleAndProduct() {
            assertThat(fingerprint(kevWith("title", "다른 제목"), unrated())).isNotEqualTo(base);
            assertThat(fingerprint(kevWith("product", "Chromium"), unrated())).isNotEqualTo(base);
        }

        @Test
        @DisplayName("분류 상위의 순서 — 건수는 넣지 않는다")
        void categoryTops() {
            assertThat(VisibleSite.fingerprint(List.of(kev(), unrated()), WEEKS, Map.of("product_family:browser",
                    List.of("CVE-2026-41204", "CVE-2026-41203")))).isNotEqualTo(base);
            assertThat(VisibleSite.fingerprint(List.of(kev(), unrated()), WEEKS, Map.of("product_family:os",
                    List.of("CVE-2026-41203", "CVE-2026-41204")))).isNotEqualTo(base);
        }

        @Test
        @DisplayName("최근 7일·그 전 7일 KEV 등재 수")
        void kevWeeks() {
            assertThat(VisibleSite.fingerprint(List.of(kev(), unrated()), KevAddedWeeks.of(10, 8), TOPS))
                    .isNotEqualTo(base);
            assertThat(VisibleSite.fingerprint(List.of(kev(), unrated()), KevAddedWeeks.of(9, 7), TOPS))
                    .isNotEqualTo(base);
        }
    }

    @Test
    @DisplayName("요약 글만 고쳐 쓰였거나 화면 지문에 넣지 않는 값(악용 확률 등)만 바뀌면 지문은 그대로다")
    void invisibleChangesKeepFingerprint() {
        String base = fingerprint(kev(), unrated());

        assertThat(fingerprint(kevWith("summary", "다른 요약"), unrated())).isEqualTo(base);
        assertThat(fingerprint(kevWith("epss", new BigDecimal("0.5")), unrated())).isEqualTo(base);
    }

    /** 시험용: kev() 줄에서 이름 붙인 값 하나만 바꿔 다시 만든다 */
    @SuppressWarnings("unchecked")
    private static TrendRowJson kevWith(String field, Object value) {
        TrendRowJson j = kev();
        return new TrendRowJson(j.id(), "title".equals(field) ? (String) value : j.title(), "score".equals(field) ? (Integer) value : j.score(),
                j.scoreBreakdown(), j.whyNow(), "kev".equals(field) ? (Boolean) value : j.isKev(),
                "ransomware".equals(field) ? (Boolean) value : j.kevRansomware(),
                "due".equals(field) ? (LocalDate) value : j.kevDueDate(),
                "added".equals(field) ? (LocalDate) value : j.kevAddedAt(), j.publishedAt(), j.pocCount(),
                "epss".equals(field) ? (BigDecimal) value : j.epss(), j.epssPercentile(), j.cvss(), j.cvssVersion(),
                "severity".equals(field) ? (String) value : j.severity(),
                "product".equals(field) ? (String) value : j.primaryProduct(), j.productFamily(), j.attackTypes(),
                "summary".equals(field) ? (String) value : j.summaryWhat(),
                "fix".equals(field) ? (List<String>) value : j.fixVersions(),
                "patch".equals(field) ? (String) value : j.patchUrl());
    }
}
