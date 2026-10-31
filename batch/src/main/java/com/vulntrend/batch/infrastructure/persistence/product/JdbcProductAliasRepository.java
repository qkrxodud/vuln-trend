package com.vulntrend.batch.infrastructure.persistence.product;

import com.vulntrend.batch.domain.product.ProductAlias;
import com.vulntrend.batch.domain.product.ProductAliasRepository;
import com.vulntrend.batch.domain.product.ProductAliases;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * product_alias 를 원본(product-alias.yml)과 똑같이 맞춘다. 지우고 다시 넣기를 한 묶음으로 반영해서
 * 원본에서 지운 줄이 남지 않게 한다(원본이 단일 출처). 표가 작아(수백 줄) 기동마다 해도 부담이 없다.
 */
@Repository
@RequiredArgsConstructor
public class JdbcProductAliasRepository implements ProductAliasRepository {

    private static final String DELETE_ALL = "delete from product_alias";

    private static final String INSERT = """
            insert into product_alias (cpe_vendor, cpe_product, slug, display_name)
            values (:vendor, :product, :slug, :displayName)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public int replaceAll(ProductAliases aliases) {
        jdbc.update(DELETE_ALL, new MapSqlParameterSource());
        SqlParameterSource[] rows = aliases.getAliases().stream()
                .map(JdbcProductAliasRepository::toRow)
                .toArray(SqlParameterSource[]::new);
        return Arrays.stream(jdbc.batchUpdate(INSERT, rows)).sum();
    }

    private static SqlParameterSource toRow(ProductAlias alias) {
        return new MapSqlParameterSource()
                .addValue("vendor", alias.getKey().getVendor())
                .addValue("product", alias.getKey().getProduct())
                .addValue("slug", alias.getSlug())
                .addValue("displayName", alias.getDisplayName());
    }
}
