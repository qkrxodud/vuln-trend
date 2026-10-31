package com.vulntrend.batch.infrastructure.persistence.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.product.ProductAlias;
import com.vulntrend.batch.domain.product.ProductAliases;
import com.vulntrend.batch.domain.product.ProductKey;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcProductAliasRepository.class})
@DisplayName("제품 이름 표준화 표 — 실제 저장 구조에서")
class JdbcProductAliasRepositoryTest {

    private static final ProductAlias CHROME = ProductAlias.of(ProductKey.of("google", "chrome"), "chrome", "Chrome");
    private static final ProductAlias CHROMIUM_V8 =
            ProductAlias.of(ProductKey.of("Google", "Chromium V8"), "chrome", "Chrome");
    private static final ProductAlias PAN_OS =
            ProductAlias.of(ProductKey.of("paloaltonetworks", "pan-os"), "pan-os", "Palo Alto PAN-OS");

    @Autowired
    private JdbcProductAliasRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("표를 넣으면 맞춰 적은 원래 이름과 표준 이름이 한 줄씩 오른다")
    void storesNormalizedNames() {
        int loaded = repository.replaceAll(ProductAliases.of(List.of(CHROME, CHROMIUM_V8, PAN_OS)));

        assertThat(loaded).isEqualTo(3);
        assertThat(stored()).containsExactlyInAnyOrder(
                Map.of("cpe_vendor", "google", "cpe_product", "chrome", "slug", "chrome", "display_name", "Chrome"),
                Map.of("cpe_vendor", "google", "cpe_product", "chromium_v8", "slug", "chrome", "display_name", "Chrome"),
                Map.of("cpe_vendor", "paloaltonetworks", "cpe_product", "pan_os",
                        "slug", "pan-os", "display_name", "Palo Alto PAN-OS"));
    }

    @Test
    @DisplayName("다시 넣으면 원본에서 지운 줄은 남지 않고 바뀐 이름은 새 이름이 된다")
    void reloadMirrorsCatalog() {
        repository.replaceAll(ProductAliases.of(List.of(CHROME, CHROMIUM_V8, PAN_OS)));
        ProductAlias renamed = ProductAlias.of(ProductKey.of("google", "chrome"), "chrome", "Google Chrome");

        repository.replaceAll(ProductAliases.of(List.of(renamed)));

        assertThat(stored()).containsExactly(
                Map.of("cpe_vendor", "google", "cpe_product", "chrome", "slug", "chrome", "display_name", "Google Chrome"));
    }

    @Test
    @DisplayName("같은 표를 두 번 넣어도 줄이 두 번 오르지 않는다")
    void sameTableTwiceIsStable() {
        ProductAliases aliases = ProductAliases.of(List.of(CHROME, PAN_OS));

        repository.replaceAll(aliases);
        repository.replaceAll(aliases);

        assertThat(stored()).hasSize(2);
    }

    private List<Map<String, Object>> stored() {
        return jdbc.queryForList("select cpe_vendor, cpe_product, slug, display_name from product_alias");
    }
}
