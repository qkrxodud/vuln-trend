package com.vulntrend.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@DisplayName("배치 시작 — 제품 이름 표준화 표")
class ProductAliasStartupTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("배치가 시작하면 저장소 안의 원본으로 표준화 표를 채운다")
    void startupFillsTable() {
        Integer products = jdbc.queryForObject("select count(distinct slug) from product_alias", Integer.class);
        String windows = jdbc.queryForObject(
                "select display_name from product_alias where cpe_vendor = 'microsoft' and cpe_product = 'windows_10_1607'",
                String.class);

        assertThat(products).isEqualTo(50);
        assertThat(windows).isEqualTo("Windows");
    }
}
