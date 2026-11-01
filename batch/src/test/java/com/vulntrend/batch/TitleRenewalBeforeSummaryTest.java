package com.vulntrend.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.summary.SummaryService;
import com.vulntrend.batch.application.vulnerability.LegacyTitleService;
import com.vulntrend.batch.domain.summary.KoreanSummary;
import com.vulntrend.batch.domain.summary.SummaryResult;
import com.vulntrend.batch.domain.vulnerability.Vulnerability;
import com.vulntrend.batch.fake.FakeSummarizer;
import com.vulntrend.batch.global.config.SummaryProperties;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import com.vulntrend.batch.infrastructure.persistence.summary.JdbcSummaryRepository;
import com.vulntrend.batch.infrastructure.persistence.vulnerability.JdbcLegacyTitleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 같은 실행 안에서 옛 규칙 제목 다시 계산과 요약 제목 올리기가 부딪히지 않는다(B1 (다), 실제 저장 구조에서).
 * 요약은 "지금 제목이 설명에서 만든 새 규칙 제목일 때만" 요약 제목으로 바꾼다 — 그래서 재계산이 먼저 돌아야 한다.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcLegacyTitleRepository.class, JdbcSummaryRepository.class})
@DisplayName("제목 다시 계산과 요약 제목의 순서")
class TitleRenewalBeforeSummaryTest {

    private static final String ID = "CVE-2026-111497";
    private static final String DESCRIPTION = "A buffer overflow vulnerability in the Brotli decoder (c/dec/decode.c) in "
            + "Google Brotli before 1.2.0 allows remote attackers to crash. More.";
    private static final String OLD_TITLE =
            "A buffer overflow vulnerability in the Brotli decoder (c/dec/decode.c) in Googl…";
    private static final String SUMMARY_WHAT = "Brotli 압축 해제에서 버퍼가 넘치는 취약점이다.";

    @Autowired
    private JdbcLegacyTitleRepository legacyTitles;

    @Autowired
    private JdbcSummaryRepository summaries;

    @Autowired
    private JdbcTemplate jdbc;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void legacyCutTitle() {
        jdbc.update("insert into vulnerability (vuln_id, title, description, trend_score, rejected) "
                + "values (?, ?, ?, ?, false)", ID, OLD_TITLE, DESCRIPTION, new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("제목을 먼저 다시 계산하고 요약하면 같은 실행에서 요약 제목이 남는다")
    void renewalThenSummaryLeavesSummaryTitle() {
        new LegacyTitleService(legacyTitles).renewAll();
        summaryService().summarizeTop();

        assertThat(title()).isEqualTo("Brotli 압축 해제에서 버퍼가 넘치는 취약점이다");
    }

    @Test
    @DisplayName("요약이 먼저 돌면 옛 제목이 남아 요약 제목을 올리지 못한다 — 그래서 재계산이 요약 앞에 있다")
    void summaryFirstMissesTitle() {
        summaryService().summarizeTop();
        new LegacyTitleService(legacyTitles).renewAll();

        assertThat(title()).isEqualTo(Vulnerability.titleOf(DESCRIPTION));
    }

    private SummaryService summaryService() {
        FakeSummarizer summarizer = new FakeSummarizer(source -> SummaryResult.from(
                KoreanSummary.of(SUMMARY_WHAT, "Brotli 를 쓰는 서비스가 영향받는다.", "원문 참고"), "gpt-6-luna", source));
        return new SummaryService(new SummaryProperties(20, "gpt-6-luna", "summary-key-41c9e7"),
                Optional.of(summarizer), summaries, clock);
    }

    private String title() {
        return jdbc.queryForObject("select title from vulnerability where vuln_id = ?", String.class, ID);
    }
}
