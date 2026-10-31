package com.vulntrend.batch.application.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.product.AffectedProductName;
import com.vulntrend.batch.domain.product.PrimaryProductCandidate;
import com.vulntrend.batch.domain.product.ProductAlias;
import com.vulntrend.batch.domain.product.ProductAliases;
import com.vulntrend.batch.domain.product.ProductKey;
import com.vulntrend.batch.domain.product.ProductMention;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.fake.FakeProductAliasCatalog;
import com.vulntrend.batch.fake.FakeProductAssignmentRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("대표 제품과 묶음 이름 매기기")
class ProductAssignServiceTest {

    private static final FakeProductAliasCatalog CATALOG = new FakeProductAliasCatalog(ProductAliases.of(List.of(
            ProductAlias.of(ProductKey.of("google", "chrome"), "chrome", "Chrome"),
            ProductAlias.of(ProductKey.of("Apache", "Log4j2"), "log4j", "Apache Log4j"),
            ProductAlias.of(ProductKey.of("apache", "log4j"), "log4j", "Apache Log4j"))));

    private static final ProductMention CHROME = mention("google", "chrome");
    private static final ProductMention LOG4J = mention("apache", "log4j");
    private static final ProductMention GIMP = mention("gimp", "gimp");

    @Test
    @DisplayName("대표 제품이 달라진 CVE 만 새 이름으로 바꾼다")
    void changesOnlyDifferentPrimary() {
        FakeProductAssignmentRepository repository = new FakeProductAssignmentRepository(List.of(
                PrimaryProductCandidate.of(CveId.from("CVE-2021-44228"), null, mention("Apache", "Log4j2"), LOG4J),
                PrimaryProductCandidate.of(CveId.from("CVE-2026-10001"), "Chrome", null, CHROME),
                PrimaryProductCandidate.of(CveId.from("CVE-2026-10002"), "Chrome", null, GIMP)), List.of());

        ProductAssignment assignment = new ProductAssignService(CATALOG, repository).assignAll();

        assertThat(repository.primaryChanges()).containsExactlyInAnyOrderEntriesOf(Map.of(
                CveId.from("CVE-2021-44228"), Optional.of("Apache Log4j"),
                CveId.from("CVE-2026-10002"), Optional.of("gimp")));
        assertThat(assignment.getPrimaryChanged()).isEqualTo(2);
    }

    @Test
    @DisplayName("제품 정보가 사라진 CVE 는 대표 제품을 비운다")
    void clearsWhenNoProductLeft() {
        FakeProductAssignmentRepository repository = new FakeProductAssignmentRepository(List.of(
                PrimaryProductCandidate.of(CveId.from("CVE-2026-10003"), "Chrome", null, null)), List.of());

        new ProductAssignService(CATALOG, repository).assignAll();

        assertThat(repository.primaryChanges()).containsExactlyEntriesOf(Map.of(
                CveId.from("CVE-2026-10003"), Optional.empty()));
    }

    @Test
    @DisplayName("영향받는 제품은 묶음 이름이 달라진 것만 바꾸고, 표에 없으면 묶음 이름을 비운다")
    void changesOnlyDifferentSlug() {
        FakeProductAssignmentRepository repository = new FakeProductAssignmentRepository(List.of(), List.of(
                AffectedProductName.of(CHROME, "chrome"),
                AffectedProductName.of(LOG4J, null),
                AffectedProductName.of(GIMP, "gimp-old")));

        ProductAssignment assignment = new ProductAssignService(CATALOG, repository).assignAll();

        assertThat(repository.slugChanges()).containsExactlyInAnyOrderEntriesOf(Map.of(
                LOG4J, Optional.of("log4j"),
                GIMP, Optional.empty()));
        assertThat(assignment.getSlugChanged()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 제품의 새로 들어온 줄만 묶음 이름이 비어 있어도 그 제품을 다시 맞춘다")
    void fillsNewRowsOfKnownProduct() {
        FakeProductAssignmentRepository repository = new FakeProductAssignmentRepository(List.of(), List.of(
                AffectedProductName.of(CHROME, "chrome"),
                AffectedProductName.of(CHROME, null)));

        new ProductAssignService(CATALOG, repository).assignAll();

        assertThat(repository.slugChanges()).containsExactlyEntriesOf(Map.of(CHROME, Optional.of("chrome")));
    }

    @Test
    @DisplayName("이미 모두 맞으면 아무것도 바꾸지 않는다")
    void nothingToChange() {
        FakeProductAssignmentRepository repository = new FakeProductAssignmentRepository(
                List.of(PrimaryProductCandidate.of(CveId.from("CVE-2026-10001"), "Chrome", null, CHROME)),
                List.of(AffectedProductName.of(CHROME, "chrome")));

        ProductAssignment assignment = new ProductAssignService(CATALOG, repository).assignAll();

        assertThat(assignment.getPrimaryChanged()).isZero();
        assertThat(assignment.getSlugChanged()).isZero();
    }

    private static ProductMention mention(String vendor, String product) {
        return ProductMention.from(vendor, product).orElseThrow();
    }
}
