package com.vulntrend.batch.application.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.export.dto.CategoriesJson;
import com.vulntrend.batch.application.export.dto.CategoryJson;
import com.vulntrend.batch.domain.product.ProductNames;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.fake.FakeCategoryRepository;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import com.vulntrend.batch.fake.FakeTrendListRepository;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("categories.json")
class CategoriesFileExportTest {

    private static final ProductNames NAMES = ProductNames.of(List.of());
    /** 한국 시각 2026-10-31 09:00 실행. 최근 7일은 10-25 00:00(한국), 30일은 10-02 00:00(한국) 부터 */
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    private final FakeTrendListRepository trends = new FakeTrendListRepository()
            .with(TrendListings.kevClassified("CVE-2026-41203", "browser", List.of("rce", "memory")))
            .with(TrendListings.kevClassified("CVE-2026-41204", "os", List.of("rce")));
    private final FakeCategoryRepository counts = new FakeCategoryRepository()
            .family("browser", 12, 40, 3)
            .attackType("rce", 30, 90, 7);
    private final FakeSiteDataWriter writer = new FakeSiteDataWriter();

    private CategoriesJson written() {
        new CategoriesFileExport(trends, counts, writer).write(NOW, NOW, NAMES);
        return writer.document("categories.json", CategoriesJson.class).orElseThrow();
    }

    @Test
    @DisplayName("공격 유형 10개와 제품군 10개를 정해진 순서와 이름으로 모두 싣는다 — 건수가 없으면 0")
    void everyCategoryInOrder() {
        CategoriesJson categories = written();

        assertThat(categories.attackTypes()).extracting(CategoryJson::key).containsExactly("rce", "injection", "xss",
                "auth", "info", "memory", "dos", "path", "request_forgery", "other");
        assertThat(categories.productFamilies()).extracting(CategoryJson::key).containsExactly("os", "browser",
                "network", "web_cms", "server", "dev", "cloud", "ics_iot", "mobile", "desktop");
        assertThat(categories.productFamilies().get(1).label()).isEqualTo("브라우저");
        CategoryJson network = categories.productFamilies().get(2);
        assertThat(List.of(network.count7d(), network.count30d(), network.kevCount())).containsOnly(0);
        assertThat(network.top()).isEmpty();
    }

    @Test
    @DisplayName("분류마다 최근 7일·30일 발행 수와 KEV 수를 싣는다 — 창은 오늘 포함 7개·30개 한국 달력일")
    void counts() {
        CategoryJson browser = written().productFamilies().get(1);

        assertThat(List.of(browser.count7d(), browser.count30d(), browser.kevCount())).containsExactly(12, 40, 3);
        assertThat(written().attackTypes().get(0).count30d()).isEqualTo(90);
        assertThat(counts.askedWeekFrom()).isEqualTo(Instant.parse("2026-10-24T15:00:00Z"));
        assertThat(counts.askedMonthFrom()).isEqualTo(Instant.parse("2026-10-01T15:00:00Z"));
    }

    @Test
    @DisplayName("분류마다 그 분류 안 트렌드 상위를 목록 줄 모양 그대로 싣는다 — 여러 유형이 붙은 CVE 는 각 유형에 든다")
    void topRows() {
        CategoriesJson categories = written();

        assertThat(categories.attackTypes().get(0).top()).extracting(row -> row.id())
                .containsExactly("CVE-2026-41203", "CVE-2026-41204");
        assertThat(categories.attackTypes().get(5).top()).extracting(row -> row.id()).containsExactly("CVE-2026-41203");
        assertThat(categories.productFamilies().get(0).top()).extracting(row -> row.id()).containsExactly("CVE-2026-41204");
        assertThat(categories.productFamilies().get(0).top().get(0).score()).isEqualTo(67);
    }

    @Test
    @DisplayName("분류 상위는 20건까지만 싣는다")
    void atMostTwenty() {
        IntStream.rangeClosed(1, 25).forEach(i -> trends.with(
                TrendListings.kevClassified("CVE-2026-%05d".formatted(70000 + i), "server", List.of())));

        assertThat(written().productFamilies().get(4).top()).hasSize(20);
    }

    @Test
    @DisplayName("분류 상위에 든 CVE 를 겹치지 않게 알린다(상세 파일 범위)")
    void listedIds() {
        Listed listed = new CategoriesFileExport(trends, counts, writer).write(NOW, NOW, NAMES);

        assertThat(listed.getIds()).containsExactlyInAnyOrder(CveId.from("CVE-2026-41203"), CveId.from("CVE-2026-41204"));
    }
}
