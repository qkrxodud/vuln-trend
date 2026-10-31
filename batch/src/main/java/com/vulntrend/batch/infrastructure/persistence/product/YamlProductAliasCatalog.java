package com.vulntrend.batch.infrastructure.persistence.product;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.product.ProductAlias;
import com.vulntrend.batch.domain.product.ProductAliasCatalog;
import com.vulntrend.batch.domain.product.ProductAliases;
import com.vulntrend.batch.domain.product.ProductKey;
import com.vulntrend.batch.global.error.exception.DomainValidationException;
import com.vulntrend.batch.infrastructure.persistence.product.dto.ProductAliasEntry;
import com.vulntrend.batch.infrastructure.persistence.product.dto.ProductAliasFile;
import com.vulntrend.batch.infrastructure.persistence.product.dto.ProductAliasName;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * 저장소 안의 product-alias.yml(src/main/resources)을 읽어 제품 이름 표준화 표로 바꾼다.
 * SnakeYAML 로 읽고 Jackson 으로 모양을 맞춘다. 모르는 항목 이름(오타)은 받지 않는다 — 조용히 빠지면 매핑이 사라진 걸 모른다.
 */
@Component
@RequiredArgsConstructor
public class YamlProductAliasCatalog implements ProductAliasCatalog {

    static final String FILE = "product-alias.yml";

    private final ObjectMapper objectMapper;

    @Override
    public ProductAliases load() {
        return read(new ClassPathResource(FILE));
    }

    ProductAliases read(Resource resource) {
        ProductAliasFile file = parse(resource);
        List<ProductAliasEntry> products = Optional.ofNullable(file).map(ProductAliasFile::products).orElseGet(List::of);
        if (products.isEmpty()) {
            throw new DomainValidationException(FILE + " lists no products.");
        }
        return ProductAliases.of(products.stream().flatMap(YamlProductAliasCatalog::toAliases).toList());
    }

    private ProductAliasFile parse(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            Object tree = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            return objectMapper.readerFor(ProductAliasFile.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue((JsonNode) objectMapper.valueToTree(tree));
        } catch (IOException | YAMLException | IllegalArgumentException unreadable) {
            throw new DomainValidationException(FILE + " could not be read as a product list ("
                    + unreadable.getClass().getSimpleName() + ").");
        }
    }

    private static Stream<ProductAlias> toAliases(ProductAliasEntry entry) {
        List<ProductAliasName> names = Stream.of(entry.cpe(), entry.kev())
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();
        if (names.isEmpty()) {
            throw new DomainValidationException("Product " + entry.slug() + " in " + FILE + " has no cpe or kev names.");
        }
        return names.stream()
                .map(name -> ProductAlias.of(ProductKey.of(name.vendor(), name.product()), entry.slug(), entry.displayName()));
    }
}
