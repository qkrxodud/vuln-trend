package com.vulntrend.batch.infrastructure.persistence.score;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.gate.dto.ProductCount;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcGateQueryRepository.class})
@DisplayName("Gate G 집계 — 실제 저장 구조에서")
class JdbcGateQueryRepositoryTest {

    @Autowired
    private JdbcGateQueryRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void vulnerabilities() {
        jdbc.update("insert into product_alias (cpe_vendor, cpe_product, slug, display_name) values "
                + "('google', 'chrome', 'chrome', 'Chrome')");
        vulnerability("CVE-2026-80001", "90", "Chrome");
        vulnerability("CVE-2026-80002", "80", "connect_secure");
        vulnerability("CVE-2026-80003", "70", "connect_secure");
        vulnerability("CVE-2026-80004", "60", "sharepoint_server");
        vulnerability("CVE-2026-80005", "50", null);
        vulnerability("CVE-2026-80006", "40", "zzz_low_rank");
        vulnerability("CVE-2026-80007", "95", "rejected_product");
        jdbc.update("update vulnerability set rejected = true where vuln_id = 'CVE-2026-80007'");
    }

    @Test
    @DisplayName("트렌드 상위 건 중 제품 표에 없는 이름을 많은 순으로 센다")
    void unmappedByFrequency() {
        assertThat(repository.unmappedProducts(5)).containsExactly(new ProductCount("connect_secure", 2),
                new ProductCount("sharepoint_server", 1));
    }

    @Test
    @DisplayName("상위 범위 밖과 철회된 CVE 는 세지 않는다")
    void topOnly() {
        assertThat(repository.unmappedProducts(2)).containsExactly(new ProductCount("connect_secure", 1));
    }

    @Test
    @DisplayName("대표 제품이 아예 없는 상위 건 수를 센다")
    void withoutProduct() {
        assertThat(repository.withoutProduct(5)).isEqualTo(1);
        assertThat(repository.withoutProduct(4)).isZero();
    }

    private void vulnerability(String id, String score, String product) {
        jdbc.update("insert into vulnerability (vuln_id, trend_score, primary_product) values (?, ?, ?)", id,
                new BigDecimal(score), product);
    }
}
