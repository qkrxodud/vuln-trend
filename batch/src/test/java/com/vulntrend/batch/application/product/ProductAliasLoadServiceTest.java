package com.vulntrend.batch.application.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.domain.product.ProductAlias;
import com.vulntrend.batch.domain.product.ProductAliasCatalog;
import com.vulntrend.batch.domain.product.ProductAliases;
import com.vulntrend.batch.domain.product.ProductKey;
import com.vulntrend.batch.fake.FakeProductAliasCatalog;
import com.vulntrend.batch.fake.FakeProductAliasRepository;
import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("제품 이름 표준화 표 적재")
class ProductAliasLoadServiceTest {

    private static final ProductAliases CHROME_AND_PHP = ProductAliases.of(List.of(
            ProductAlias.of(ProductKey.of("google", "chrome"), "chrome", "Chrome"),
            ProductAlias.of(ProductKey.of("Google", "Chromium V8"), "chrome", "Chrome"),
            ProductAlias.of(ProductKey.of("php", "php"), "php", "PHP")));

    private final FakeProductAliasRepository repository = new FakeProductAliasRepository();

    @Test
    @DisplayName("저장소 안의 원본을 읽어 표 전체를 그 내용으로 바꾼다")
    void replacesWithCatalog() {
        ProductAliasLoadService service = new ProductAliasLoadService(new FakeProductAliasCatalog(CHROME_AND_PHP), repository);

        int loaded = service.load();

        assertThat(loaded).isEqualTo(3);
        assertThat(repository.storedSlugs()).containsExactlyInAnyOrder("chrome", "php");
    }

    @Test
    @DisplayName("배치가 시작할 때 한 번 적재한다")
    void loadsOnStartup() {
        ProductAliasLoadService service = new ProductAliasLoadService(new FakeProductAliasCatalog(CHROME_AND_PHP), repository);

        service.loadOnStartup();

        assertThat(repository.stored()).isPresent();
    }

    @Test
    @DisplayName("원본을 읽을 수 없으면 표를 건드리지 않고 시작을 멈춘다")
    void brokenCatalogStopsStartup() {
        ProductAliasCatalog broken = () -> {
            throw new DomainValidationException("product-alias.yml is broken.");
        };
        ProductAliasLoadService service = new ProductAliasLoadService(broken, repository);

        assertThatThrownBy(service::loadOnStartup).isInstanceOf(DomainValidationException.class);
        assertThat(repository.stored()).isEmpty();
    }
}
