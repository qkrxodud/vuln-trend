package com.vulntrend.batch.infrastructure.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.domain.publish.PublishResult;
import com.vulntrend.batch.global.config.OutputProperties;
import com.vulntrend.batch.global.config.PublishProperties;
import com.vulntrend.batch.global.error.exception.PublishException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** 실제 git 으로, 임시 폴더의 bare 저장소를 원격으로 둔 시험. 실제 레포와 원격은 건드리지 않는다 */
@DisplayName("사이트 데이터 게시")
@ExtendWith(OutputCaptureExtension.class)
class GitPusherTest {

    /** 한국 시각 2026-10-31 09:00 */
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @TempDir
    private Path root;

    private GitRepoFixture repo;

    @BeforeEach
    void setUp() throws IOException {
        repo = GitRepoFixture.create(root);
    }

    @Nested
    @DisplayName("데이터 파일이 바뀌었으면")
    class Changed {

        @Test
        @DisplayName("한국 시각을 단 커밋 하나로 원격에 올린다")
        void commitsAndPushes() throws IOException {
            repo.write(GitRepoFixture.DATA + "/meta.json", "{\"version\": 2}\n");

            PublishResult result = pusher(repo.dataDir()).publish();

            assertThat(result).isEqualTo(PublishResult.PUBLISHED);
            assertThat(repo.remoteSubjects()).containsExactly("data: 2026-10-31 09:00 KST", "init");
        }

        @Test
        @DisplayName("새로 생긴 파일과 지워진 파일도 함께 올린다")
        void addsAndRemoves() throws IOException {
            repo.write(GitRepoFixture.DATA + "/cve/CVE-2021-44228.json", "{}\n");
            repo.delete(GitRepoFixture.DATA + "/meta.json");

            pusher(repo.dataDir()).publish();

            assertThat(repo.remoteLastCommitFiles())
                    .containsExactlyInAnyOrder("web/public/data/cve/CVE-2021-44228.json", "web/public/data/meta.json");
        }

        @Test
        @DisplayName("데이터 폴더 밖에서 바뀐 코드는 이미 올려 둔 것이라도 커밋에 섞지 않는다")
        void leavesCodeChangesAlone() throws IOException {
            repo.write("web/src/App.tsx", "export const App = 2\n");
            repo.stage("web/src/App.tsx");
            repo.write(GitRepoFixture.DATA + "/meta.json", "{\"version\": 2}\n");

            pusher(repo.dataDir()).publish();

            assertThat(repo.remoteLastCommitFiles()).containsExactly("web/public/data/meta.json");
            assertThat(repo.stagedFiles()).containsExactly("web/src/App.tsx");
        }
    }

    @Test
    @DisplayName("데이터 파일이 그대로면 커밋하지도 올리지도 않는다")
    void unchanged() throws IOException {
        PublishResult result = pusher(repo.dataDir()).publish();

        assertThat(result).isEqualTo(PublishResult.UNCHANGED);
        assertThat(repo.remoteSubjects()).containsExactly("init");
    }

    @Nested
    @DisplayName("올리지 못하면")
    class Failure {

        @Test
        @DisplayName("게시 실패로 알린다")
        void pushFails() throws IOException {
            repo.setRemoteUrl(root.resolve("missing.git").toString());
            repo.write(GitRepoFixture.DATA + "/meta.json", "{\"version\": 2}\n");

            assertThatThrownBy(() -> pusher(repo.dataDir()).publish())
                    .isInstanceOf(PublishException.class)
                    .hasMessageContaining("push");
        }

        @Test
        @DisplayName("원격 주소에 든 인증 정보는 실패 사유에도 실행 기록에도 남지 않는다")
        void credentialsNeverLeak(CapturedOutput output) throws IOException {
            repo.setRemoteUrl("http://batch:secret-token-9x@127.0.0.1:9/vuln-trend.git");
            repo.write(GitRepoFixture.DATA + "/meta.json", "{\"version\": 2}\n");

            assertThatThrownBy(() -> pusher(repo.dataDir()).publish())
                    .isInstanceOf(PublishException.class)
                    .message().doesNotContain("secret-token-9x").doesNotContain("127.0.0.1");
            assertThat(output).doesNotContain("secret-token-9x");
        }
    }

    @Nested
    @DisplayName("git 이 띄운 도우미가 사람의 입력을 기다리면")
    class WaitingHelper {

        @Test
        @DisplayName("입력이 곧바로 끝나 시간 한도까지 기다리지 않고 게시를 마친다")
        void doesNotWaitForInput() throws IOException {
            repo.installInputReadingHelper(root.resolve("seen.txt"));
            repo.write(GitRepoFixture.DATA + "/meta.json", "{\"version\": 2}\n");

            PublishResult result = pusher(repo.dataDir(), Duration.ofSeconds(5)).publish();

            assertThat(result).isEqualTo(PublishResult.PUBLISHED);
            assertThat(repo.remoteSubjects()).containsExactly("data: 2026-10-31 09:00 KST", "init");
        }

        @Test
        @DisplayName("비밀번호를 묻는 프로그램은 묻지 않고 곧바로 실패하는 것으로 바뀌어 있다")
        void askPassFailsAtOnce() throws IOException {
            Path seen = root.resolve("seen.txt");
            repo.installInputReadingHelper(seen);
            repo.write(GitRepoFixture.DATA + "/meta.json", "{\"version\": 2}\n");

            pusher(repo.dataDir(), Duration.ofSeconds(5)).publish();

            assertThat(Files.readString(seen)).isEqualTo("/usr/bin/false|/usr/bin/false\n");
        }
    }

    @Test
    @DisplayName("git 명령이 시간 한도 안에 끝나지 않으면 기다리지 않고 게시 실패로 알린다")
    void timesOut() throws IOException {
        repo.write(GitRepoFixture.DATA + "/meta.json", "{\"version\": 2}\n");
        PublishProperties hurried = new PublishProperties(repo.work().toString(), "origin", Duration.ofNanos(1));
        GitPusher pusher = new GitPusher(hurried, new OutputProperties(repo.dataDir().toString()),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(pusher::publish)
                .isInstanceOf(PublishException.class)
                .hasMessageContaining("did not finish");
    }

    @Test
    @DisplayName("데이터 폴더가 저장소 밖에 있으면 아무것도 올리지 않는다")
    void dataOutsideRepository() {
        assertThatThrownBy(() -> pusher(root.resolve("elsewhere")).publish())
                .isInstanceOf(PublishException.class)
                .hasMessageContaining("outside the repository");
    }

    private GitPusher pusher(Path dataDir) {
        return pusher(dataDir, Duration.ofSeconds(30));
    }

    private GitPusher pusher(Path dataDir, Duration commandTimeout) {
        PublishProperties properties = new PublishProperties(repo.work().toString(), "origin", commandTimeout);
        return new GitPusher(properties, new OutputProperties(dataDir.toString()), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
