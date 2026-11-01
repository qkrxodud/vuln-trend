package com.vulntrend.batch.infrastructure.publish;

import com.vulntrend.batch.domain.publish.PublishResult;
import com.vulntrend.batch.domain.publish.Publisher;
import com.vulntrend.batch.global.config.OutputProperties;
import com.vulntrend.batch.global.config.PublishProperties;
import com.vulntrend.batch.global.error.exception.PublishException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 사이트 데이터를 git 으로 게시한다(기획서 6장 9번, WBS 4.3).
 * - 대상은 데이터 폴더(output.dir)뿐이다: add·commit 모두 그 경로로만 한정한다. 저장소 전체 git add -A 는 쓰지 않는다 —
 *   같은 작업 폴더에서 고치던 코드(이미 올려 둔 것 포함)가 데이터 커밋에 섞이면 안 된다.
 * - 데이터가 그대로면 새 커밋은 만들지 않고 push 만 한다 — 게시하기로 정한 실행만 부르므로(SitePublishService),
 *   앞선 실행이 push 에 실패해 남은 데이터 커밋을 올리고, 원격에 닿지 못하면 실패로 알린다(2시간마다 실행, 2026-11-01).
 * - 성공은 종료 코드로만 판정한다. git 출력은 읽지도 로그에 남기지도 않는다(원격 주소·인증 정보가 들어 있을 수 있음).
 * - 자격 증명을 묻느라 멈추지 않게 GIT_TERMINAL_PROMPT=0 으로 돌리고, 명령마다 시간 한도를 둔다.
 * - 기다릴 이유가 생기면 멈추지 말고 곧바로 실패해 종료 코드로 드러나게 한다(launchd 실행 push 2분 초과, 2026-11-01):
 *   표준 입력은 /dev/null(git 과 git 이 띄운 도우미가 물려받는다), GIT_ASKPASS·SSH_ASKPASS 는 묻지 않고 실패하는 /usr/bin/false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitPusher implements Publisher {

    private static final ZoneId SITE_CALENDAR = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter COMMIT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'KST'");
    private static final int SUCCESS = 0;
    // git diff --quiet: 0 = 같음, 1 = 다름, 그 밖 = 오류
    private static final int UNCHANGED = 0;
    private static final int CHANGED = 1;
    private static final File NO_INPUT = new File("/dev/null");
    private static final String FAIL_AT_ONCE = "/usr/bin/false";

    private final PublishProperties properties;
    private final OutputProperties output;
    private final Clock clock;

    @Override
    public PublishResult publish() {
        Path repository = Path.of(properties.getRepoDir()).toAbsolutePath().normalize();
        String data = dataPathIn(repository);
        requireSuccess("add", git(repository, "add", "-A", "--", data));
        int difference = git(repository, "diff", "--cached", "--quiet", "--", data);
        if (difference == UNCHANGED) {
            push(repository);
            log.info("Site data is unchanged; no new commit, and any earlier data commit is pushed to {}.",
                    properties.getRemote());
            return PublishResult.UNCHANGED;
        }
        requireExit("diff", difference, CHANGED);
        requireSuccess("commit", git(repository, "commit", "-q", "-m", commitMessage(), "--", data));
        push(repository);
        log.info("Site data committed and pushed to {}.", properties.getRemote());
        return PublishResult.PUBLISHED;
    }

    // 데이터가 그대로여도 올린다 — 앞선 실행이 커밋만 하고 push 에 실패했으면 그 커밋이 이번에 올라간다(원격이 같으면 아무 일 없음)
    private void push(Path repository) {
        requireSuccess("push", git(repository, "push", "-q", properties.getRemote(), "HEAD"));
    }

    // 저장소 기준 데이터 폴더 경로. 저장소 밖이면 게시하지 않는다
    private String dataPathIn(Path repository) {
        Path data = Path.of(output.getDir()).toAbsolutePath().normalize();
        if (data.startsWith(repository)) {
            return repository.relativize(data).toString();
        }
        throw new PublishException("The data folder is outside the repository; nothing was published.");
    }

    private String commitMessage() {
        return "data: " + COMMIT_TIME.format(clock.instant().atZone(SITE_CALENDAR));
    }

    /** git 을 돌려 종료 코드를 돌려준다. 단계 이름은 첫 인자(add·diff·commit·push) */
    private int git(Path repository, String... args) {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(repository.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(NO_INPUT))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GIT_ASKPASS", FAIL_AT_ONCE);
        builder.environment().put("SSH_ASKPASS", FAIL_AT_ONCE);
        return run(builder, args[0]);
    }

    private int run(ProcessBuilder builder, String step) {
        try {
            Process process = builder.start();
            if (process.waitFor(properties.getCommandTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                return process.exitValue();
            }
            process.destroyForcibly();
            throw new PublishException("git " + step + " did not finish within " + properties.getCommandTimeout() + ".");
        } catch (IOException e) {
            throw new PublishException("git " + step + " could not start (" + e.getClass().getSimpleName() + ").");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishException("git " + step + " was interrupted.");
        }
    }

    private static void requireSuccess(String step, int exit) {
        requireExit(step, exit, SUCCESS);
    }

    private static void requireExit(String step, int exit, int expected) {
        if (exit != expected) {
            throw new PublishException("git " + step + " failed with exit code " + exit + ".");
        }
    }
}
