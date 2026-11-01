package com.vulntrend.batch.application.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.product.ProductNames;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("트렌드 목록 한 줄")
class TrendRowsTest {

    private static final ProductNames NAMES = ProductNames.of(List.of());

    @Test
    @DisplayName("화면 점수는 소수 1자리 항의 합을 반올림한 정수이고 점수 구성을 함께 싣는다")
    void scoreAndBreakdown() {
        TrendRowJson row = TrendRows.from(TrendListings.kev("CVE-2026-41203"), NAMES);

        assertThat(row.score()).isEqualTo(67);
        assertThat(row.scoreBreakdown().epss()).isEqualTo(new BigDecimal("28.3"));
        assertThat(row.scoreBreakdown().decay()).isEqualTo(new BigDecimal("-21.3"));
    }

    @Test
    @DisplayName("왜 지금? 은 규칙 템플릿으로 고른다")
    void whyNowFromTemplate() {
        assertThat(TrendRows.from(TrendListings.kev("CVE-2026-41203"), NAMES).whyNow()).isEqualTo("CISA 악용 목록 등재");
        assertThat(TrendRows.from(TrendListings.unrated("CVE-2026-50001"), NAMES).whyNow()).isEqualTo("CVSS 미평가, 최근 공개");
    }

    @Test
    @DisplayName("저장 값을 계약 이름 그대로 옮긴다")
    void storedValues() {
        TrendRowJson row = TrendRows.from(TrendListings.kev("CVE-2026-41203"), NAMES);

        assertThat(row.id()).isEqualTo("CVE-2026-41203");
        assertThat(row.title()).isEqualTo("Chrome V8 타입 혼동");
        assertThat(row.isKev()).isTrue();
        assertThat(row.kevDueDate()).isEqualTo(LocalDate.parse("2026-11-03"));
        assertThat(row.kevAddedAt()).isEqualTo(LocalDate.parse("2026-10-19"));
        assertThat(row.epss()).isEqualTo(new BigDecimal("0.94310"));
        assertThat(row.epssPercentile()).isEqualTo(new BigDecimal("0.99900"));
        assertThat(row.cvss()).isEqualTo(new BigDecimal("9.8"));
        assertThat(row.cvssVersion()).isEqualTo("3.1");
        assertThat(row.severity()).isEqualTo("CRITICAL");
        assertThat(row.primaryProduct()).isEqualTo("Chrome");
        assertThat(row.summaryWhat()).isEqualTo("악성 웹페이지로 원격 코드 실행");
    }

    @Test
    @DisplayName("KEV 에 올라 랜섬웨어에 쓰이면 랜섬웨어 표시와 문구를 싣는다")
    void kevRansomware() {
        TrendRowJson row = TrendRows.from(TrendListings.kevRansomware("CVE-2026-41203"), NAMES);

        assertThat(row.kevRansomware()).isTrue();
        assertThat(row.whyNow()).isEqualTo("랜섬웨어 캠페인에 사용 중");
    }

    @Test
    @DisplayName("KEV 가 아니면 랜섬웨어는 아니고 조치 기한도 등재일도 없다")
    void notKev() {
        TrendRowJson row = TrendRows.from(TrendListings.unrated("CVE-2026-50001"), NAMES);

        assertThat(row.kevRansomware()).isFalse();
        assertThat(row.kevDueDate()).isNull();
        assertThat(row.kevAddedAt()).isNull();
    }

    @Test
    @DisplayName("제목이 아직 없으면 CVE 번호를 제목으로 쓴다")
    void titleFallsBackToId() {
        assertThat(TrendRows.from(TrendListings.unrated("CVE-2026-50001"), NAMES).title()).isEqualTo("CVE-2026-50001");
    }

    @Test
    @DisplayName("표에 없는 대표 제품은 저장 원문 대신 사람이 읽는 이름으로 싣는다")
    void unmappedProductReadable() {
        TrendRowJson row = TrendRows.from(TrendListings.kevWithProduct("CVE-2026-41203", "panorama_virtual_appliance"),
                NAMES);

        assertThat(row.primaryProduct()).isEqualTo("Panorama Virtual Appliance");
    }

    @Test
    @DisplayName("표의 표시 이름은 소문자여도 표에 적힌 대로 싣는다")
    void tableNameAsWritten() {
        TrendRowJson row = TrendRows.from(TrendListings.kevWithProduct("CVE-2026-41203", "curl"),
                ProductNames.of(List.of("curl")));

        assertThat(row.primaryProduct()).isEqualTo("curl");
    }

    @Test
    @DisplayName("대표 제품이 없으면 비워 둔다")
    void noProduct() {
        assertThat(TrendRows.from(TrendListings.unrated("CVE-2026-50001"), NAMES).primaryProduct()).isNull();
    }
}
