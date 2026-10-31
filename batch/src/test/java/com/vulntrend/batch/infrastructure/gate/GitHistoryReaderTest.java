package com.vulntrend.batch.infrastructure.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.domain.gate.dto.DataCommit;
import com.vulntrend.batch.domain.gate.dto.RepoSize;
import com.vulntrend.batch.global.config.OutputProperties;
import com.vulntrend.batch.global.config.PublishProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 시험마다 임시 폴더에 새 git 저장소를 만든다. 실제 저장소와 사용자 git 설정은 건드리지 않는다 */
@DisplayName("git 기록 읽기")
class GitHistoryReaderTest {

    @TempDir
    private Path repo;

    private Path data;

    @BeforeEach
    void repository() throws IOException, InterruptedException {
        data = Files.createDirectories(repo.resolve("web/public/data"));
        git("init", "-q");
    }

    @Test
    @DisplayName("데이터 커밋만 최신부터 실행 시각과 바뀐 데이터 파일 수로 읽는다")
    void dataCommitsNewestFirst() throws IOException, InterruptedException {
        commitData("data: 2026-11-01 09:00 KST", "meta.json", "index.json");
        Files.writeString(repo.resolve("README.md"), "코드");
        git("add", "README.md");
        git("commit", "-q", "-m", "feat: 코드 커밋");
        commitData("data: 2026-11-02 09:00 KST", "meta.json", "index.json", "cve/CVE-2026-41203.json");

        List<DataCommit> commits = reader(Duration.ofSeconds(30)).recentDataCommits(10);

        assertThat(commits).containsExactly(new DataCommit(LocalDateTime.parse("2026-11-02T09:00"), 3),
                new DataCommit(LocalDateTime.parse("2026-11-01T09:00"), 2));
    }

    @Test
    @DisplayName("요청한 개수만큼만 읽고 시각을 읽을 수 없는 data 커밋은 건너뛴다")
    void limitAndUnreadableSubjects() throws IOException, InterruptedException {
        commitData("data: 2026-11-01 09:00 KST", "meta.json");
        commitData("data: 손으로 만든 커밋", "index.json");
        commitData("data: 2026-11-02 09:00 KST", "stats.json");
        commitData("data: 2026-11-03 09:00 KST", "recent.json");

        List<DataCommit> commits = reader(Duration.ofSeconds(30)).recentDataCommits(2);

        assertThat(commits).extracting(DataCommit::runAt).containsExactly(LocalDateTime.parse("2026-11-03T09:00"),
                LocalDateTime.parse("2026-11-02T09:00"));
    }

    @Test
    @DisplayName("저장소 크기는 압축해 묶은 기록과 낱개 기록의 바이트를 더해 읽는다")
    void size() throws IOException, InterruptedException {
        commitData("data: 2026-11-01 09:00 KST", "meta.json");

        RepoSize size = reader(Duration.ofSeconds(30)).size();

        assertThat(size.packedBytes() + size.looseBytes()).isPositive();
    }

    @Test
    @DisplayName("git 저장소가 아니면 읽지 못했다고 알린다")
    void notRepository(@TempDir Path elsewhere) {
        GitHistoryReader reader = new GitHistoryReader(new PublishProperties(elsewhere.toString(), "origin",
                Duration.ofSeconds(30)), new OutputProperties(elsewhere.toString()));

        assertThatThrownBy(() -> reader.recentDataCommits(10)).isInstanceOf(SourceFetchException.class)
                .hasMessageNotContaining(elsewhere.toString());
    }

    @Test
    @DisplayName("데이터 폴더가 저장소 밖이면 읽지 않는다")
    void dataOutsideRepository(@TempDir Path elsewhere) {
        GitHistoryReader reader = new GitHistoryReader(new PublishProperties(repo.toString(), "origin",
                Duration.ofSeconds(30)), new OutputProperties(elsewhere.toString()));

        assertThatThrownBy(() -> reader.recentDataCommits(10)).isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("outside the repository");
    }

    @Test
    @DisplayName("시간 한도 안에 끝나지 않으면 기다리지 않고 읽지 못했다고 알린다")
    void timeout() throws IOException, InterruptedException {
        commitData("data: 2026-11-01 09:00 KST", "meta.json");

        assertThatThrownBy(() -> reader(Duration.ofNanos(1)).size()).isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("did not finish");
    }

    private GitHistoryReader reader(Duration timeout) {
        return new GitHistoryReader(new PublishProperties(repo.toString(), "origin", timeout),
                new OutputProperties(data.toString()));
    }

    private void commitData(String message, String... files) throws IOException, InterruptedException {
        for (String file : files) {
            Path path = data.resolve(file);
            Files.createDirectories(path.getParent());
            Files.writeString(path, message + file);
        }
        git("add", "-A", "--", "web/public/data");
        git("commit", "-q", "-m", message);
    }

    private void git(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git", "-c", "user.name=시험", "-c", "user.email=test@example.com",
                "-c", "commit.gpgsign=false", "-c", "init.defaultBranch=main"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(process.waitFor()).as(String.join(" ", args)).isZero();
    }
}
