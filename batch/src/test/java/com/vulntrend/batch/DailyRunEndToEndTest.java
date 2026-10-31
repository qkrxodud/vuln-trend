package com.vulntrend.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.vulntrend.batch.application.collect.CollectService;
import com.vulntrend.batch.application.export.ExportService;
import com.vulntrend.batch.application.gate.GateCheckService;
import com.vulntrend.batch.application.product.ProductAssignService;
import com.vulntrend.batch.application.score.ScoreService;
import com.vulntrend.batch.application.stat.DailyStatService;
import com.vulntrend.batch.application.summary.SummaryService;
import com.vulntrend.batch.domain.publish.Publisher;
import com.vulntrend.batch.domain.summary.KoreanSummary;
import com.vulntrend.batch.domain.summary.Summarizer;
import com.vulntrend.batch.domain.summary.SummaryResult;
import com.vulntrend.batch.fake.FakeSummarizer;
import com.vulntrend.batch.global.error.exception.CollectionFailedException;
import com.vulntrend.batch.global.error.exception.OutputWriteException;
import com.vulntrend.batch.infrastructure.feed.FeedStubServer;
import com.vulntrend.batch.infrastructure.nvd.NvdSamples;
import com.vulntrend.batch.infrastructure.nvd.NvdStubServer;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import com.vulntrend.batch.infrastructure.publish.GitRepoFixture;
import com.vulntrend.batch.runner.BatchRunner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * WBS 4.4 끝에서 끝까지: 실제 설정·실제 저장 구조(Postgres)·실제 수집기로 하루 한 번 실행을 돌린다.
 * NVD·KEV·EPSS 는 흉내 서버, Claude 는 가짜 Summarizer, git 원격은 임시 폴더의 bare 저장소다(실제 레포·원격은 건드리지 않는다).
 * 수집 → 대표 제품 → 점수 → 일별 통계 → 요약 → 사이트 데이터 파일 → git push 까지 한 번에 확인한다.
 * 시험끼리 같은 원격을 쓰므로 원격 확인은 "실행 전보다 몇 개 늘었나"로 한다. "5분 이내"는 실데이터 실측이라 NVD 키 뒤로 미룬다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, DailyRunEndToEndTest.ClaudeStandIn.class})
@DisplayName("하루 한 번 실행 — 끝에서 끝까지")
class DailyRunEndToEndTest {

    @TempDir
    static Path root;

    private static NvdStubServer nvd;
    private static NvdStubServer kev;
    private static FeedStubServer epss;
    private static GitRepoFixture site;

    @Autowired
    private CollectService collect;
    @Autowired
    private ProductAssignService productAssignment;
    @Autowired
    private ScoreService score;
    @Autowired
    private DailyStatService dailyStat;
    @Autowired
    private SummaryService summary;
    @Autowired
    private ExportService export;
    @Autowired
    private Publisher publisher;
    @Autowired
    private GateCheckService gate;
    @Autowired
    private FakeSummarizer claude;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    static void startSources() throws IOException {
        nvd = new NvdStubServer();
        kev = new NvdStubServer();
        epss = new FeedStubServer();
        site = GitRepoFixture.create(root);
    }

    @AfterAll
    static void stopSources() {
        nvd.close();
        kev.close();
        epss.close();
    }

    @DynamicPropertySource
    static void sources(DynamicPropertyRegistry registry) {
        registry.add("nvd.base-url", nvd::baseUrl);
        registry.add("nvd.request-interval", () -> "0ms");
        registry.add("nvd.max-attempts", () -> "1");
        registry.add("kev.url", () -> kev.baseUrl() + "/known_exploited_vulnerabilities.json");
        registry.add("kev.max-attempts", () -> "1");
        registry.add("epss.url", () -> epss.url());
        registry.add("epss.max-attempts", () -> "1");
        registry.add("output.dir", () -> site.dataDir().toString());
        registry.add("publish.repo-dir", () -> site.work().toString());
    }

    @BeforeEach
    void emptyStores() {
        jdbc.execute("truncate summary_call, summary, daily_stat, affected_product, reference_link, "
                + "vulnerability_raw, vulnerability, sync_state cascade");
    }

    @Test
    @DisplayName("한 번 돌리면 세 소스를 모두 가져오고 점수·일별 통계·한국어 요약까지 남긴다")
    void firstRun() throws IOException {
        runner().run();

        assertThat(jdbc.queryForList("select source from sync_state where status = 'SUCCESS' order by source",
                String.class)).containsExactly("epss", "kev", "nvd");
        assertThat(jdbc.queryForMap("select is_kev, epss_score from vulnerability where vuln_id = 'CVE-2021-44228'"))
                .containsEntry("is_kev", true)
                .hasEntrySatisfying("epss_score", value -> assertThat(value.toString()).isEqualTo("0.94400"));
        assertThat(jdbc.queryForObject("select count(*) from vulnerability where trend_score > 0", Integer.class))
                .isPositive();
        assertThat(jdbc.queryForObject("select count(*) from daily_stat", Integer.class)).isEqualTo(14);
        assertThat(jdbc.queryForObject("select count(*) from summary", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select title from vulnerability where vuln_id = 'CVE-2021-44228'",
                String.class)).isEqualTo("CVE-2021-44228 은 원격 코드 실행 취약점이다");
    }

    @Test
    @DisplayName("한 번 돌리면 데이터 폴더에 목록 5종과 CVE 상세 파일을 쓰고 원격에 데이터 커밋 하나를 올린다")
    void firstRunPublishes() throws IOException {
        int before = site.remoteSubjects().size();

        runner().run();

        assertThat(Files.list(site.dataDir()).map(path -> path.getFileName().toString()))
                .contains("meta.json", "stats.json", "index.json", "recent.json", "search-index.json", "cve");
        assertThat(Files.list(site.dataDir().resolve("cve")).map(path -> path.getFileName().toString()))
                .contains("CVE-2021-44228.json");
        assertThat(site.remoteSubjects()).hasSize(before + 1);
        assertThat(site.remoteSubjects().get(0)).startsWith("data: ").endsWith(" KST");
        assertThat(site.remoteLastCommitFiles()).allMatch(file -> file.startsWith(GitRepoFixture.DATA + "/"));
        assertThat(site.uncommittedData()).isEmpty();
    }

    @Test
    @DisplayName("두 번째 실행에서 내용이 그대로인 파일은 다시 쓰지 않아 데이터 커밋에 들어가지 않는다")
    void secondRunLeavesUnchangedFilesAlone() throws IOException {
        runner().run();
        String detailBefore = Files.readString(site.dataDir().resolve("cve/CVE-2021-44228.json"));
        int before = site.remoteSubjects().size();

        runner().run();

        assertThat(Files.readString(site.dataDir().resolve("cve/CVE-2021-44228.json"))).isEqualTo(detailBefore);
        if (site.remoteSubjects().size() > before) {
            assertThat(site.remoteLastCommitFiles())
                    .doesNotContain(GitRepoFixture.DATA + "/cve/CVE-2021-44228.json",
                            GitRepoFixture.DATA + "/search-index.json");
        }
        assertThat(site.uncommittedData()).isEmpty();
    }

    @Test
    @DisplayName("데이터 파일을 쓰지 못하면 아무것도 올리지 않고 종료 코드 1 로 끝난다")
    void outputFailureStopsPush() throws IOException {
        runner().run();
        int before = site.remoteSubjects().size();
        Path blocked = site.dataDir().resolve("stats.json");
        Files.delete(blocked);
        Files.createDirectory(blocked);
        try {
            Throwable failure = catchThrowable(runner()::run);

            assertThat(failure).isInstanceOf(OutputWriteException.class);
            assertThat(((ExitCodeGenerator) failure).getExitCode()).isEqualTo(1);
            assertThat(site.remoteSubjects()).hasSize(before);
        } finally {
            Files.delete(blocked);
        }
    }

    @Test
    @DisplayName("수집이 실패하면 점수부터 게시까지 건너뛰고 종료 코드 1 로 끝난다")
    void collectionFailureExitsWithOne() throws IOException {
        int before = site.remoteSubjects().size();
        answerSources(500);

        Throwable failure = catchThrowable(() -> assemble().run());

        assertThat(failure).isInstanceOf(CollectionFailedException.class).hasMessageContaining("KEV");
        assertThat(((ExitCodeGenerator) failure).getExitCode()).isEqualTo(1);
        assertThat(site.remoteSubjects()).hasSize(before);
        assertThat(jdbc.queryForObject("select count(*) from summary", Integer.class)).isZero();
    }

    @Test
    @DisplayName("두 번째 실행은 이미 요약한 CVE 를 Claude 에 다시 묻지 않는다")
    void secondRunDoesNotResummarize() throws IOException {
        runner().run();
        int askedAfterFirst = claude.asked().size();

        runner().run();

        assertThat(claude.asked()).hasSize(askedAfterFirst);
        assertThat(jdbc.queryForObject("select count(*) from summary", Integer.class)).isEqualTo(2);
    }

    // 테스트 프로필은 러너 빈을 등록하지 않는다(시작하자마자 돌지 않게). 같은 빈들로 직접 만든다
    private BatchRunner runner() throws IOException {
        answerSources(200);
        return assemble();
    }

    private BatchRunner assemble() {
        return new BatchRunner(collect, productAssignment, score, dailyStat, summary, export, publisher, gate);
    }

    // 흉내 서버는 준비한 답을 한 번씩만 쓰므로 실행마다 세 소스의 답을 새로 넣는다. kevStatus 가 200 이 아니면 KEV 만 실패
    private static void answerSources(int kevStatus) throws IOException {
        nvd.pageOf(List.of(NvdSamples.log4shell(), NvdSamples.CHROME));
        kev.answer(kevStatus, kevStatus == 200 ? resource("/kev/catalog-sample.json") : "down");
        epss.answer(200, gzip("#model_version:v2026.06.15,score_date:2026-10-30T12:03:08Z", "cve,epss,percentile",
                "CVE-2021-44228,0.94400,0.99950"));
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = DailyRunEndToEndTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] gzip(String... lines) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    /** Claude 대신 답하는 가짜. 요약 열쇠가 없어 실제 클라이언트는 만들어지지 않는다 */
    @TestConfiguration(proxyBeanMethods = false)
    static class ClaudeStandIn {

        @Bean
        FakeSummarizer claudeStandIn() {
            return new FakeSummarizer(source -> SummaryResult.from(KoreanSummary.of(
                    source.getCveId() + " 은 원격 코드 실행 취약점이다.", "원문 참고", "원문 참고"),
                    "claude-opus-5-5", source));
        }
    }
}
