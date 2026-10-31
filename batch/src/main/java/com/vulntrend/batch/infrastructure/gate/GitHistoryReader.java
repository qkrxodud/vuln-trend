package com.vulntrend.batch.infrastructure.gate;

import com.vulntrend.batch.domain.gate.RepositoryHistory;
import com.vulntrend.batch.domain.gate.dto.DataCommit;
import com.vulntrend.batch.domain.gate.dto.RepoSize;
import com.vulntrend.batch.global.config.OutputProperties;
import com.vulntrend.batch.global.config.PublishProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * git 기록 읽기(Gate G). 읽기 명령(log·count-objects)만 쓰고 저장소·설정은 바꾸지 않는다.
 * - 데이터 커밋: GitPusher 가 남긴 "data: yyyy-MM-dd HH:mm KST" 제목을 데이터 폴더 경로로 한정해 읽고, 그 커밋에서 바뀐 데이터 파일 수를 센다.
 *   제목에서 시각을 읽을 수 없는 data 커밋(손으로 만든 커밋 등)은 건너뛴다
 * - 크기: count-objects -v 의 size-pack(묶인 객체)·size(낱개 객체), 단위 KiB
 * - 읽은 출력은 로그에 남기지 않는다. 자격 증명을 묻느라 멈추지 않게 GIT_TERMINAL_PROMPT=0, 명령마다 publish.command-timeout
 * - 실패하면 SourceFetchException("git"). 상세에 경로·출력은 넣지 않는다
 */
@Component
@RequiredArgsConstructor
public class GitHistoryReader implements RepositoryHistory {

    private static final String SOURCE = "git";
    private static final String DATA_PREFIX = "data: ";
    private static final DateTimeFormatter SUBJECT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'KST'");
    private static final String RECORD = "\u001e";
    private static final int EXTRA = 20;
    private static final long KIB = 1024L;

    private final PublishProperties publish;
    private final OutputProperties output;

    @Override
    public List<DataCommit> recentDataCommits(int limit) {
        Path repository = repository();
        String log = git(repository, "log", "-n", String.valueOf(limit + EXTRA), "--format=" + RECORD + "%s",
                "--name-only", "--grep=^" + DATA_PREFIX, "--", dataPathIn(repository));
        return Arrays.stream(log.split(RECORD))
                .map(String::strip)
                .filter(record -> record.startsWith(DATA_PREFIX))
                .map(GitHistoryReader::commit)
                .flatMap(Optional::stream)
                .limit(limit)
                .toList();
    }

    @Override
    public RepoSize size() {
        Map<String, Long> counts = git(repository(), "count-objects", "-v").lines()
                .map(line -> line.split(":\\s*", 2))
                .filter(pair -> pair.length == 2 && pair[1].strip().matches("\\d+"))
                .collect(Collectors.toMap(pair -> pair[0].strip(), pair -> Long.parseLong(pair[1].strip()),
                        (first, second) -> first));
        return new RepoSize(counts.getOrDefault("size-pack", 0L) * KIB, counts.getOrDefault("size", 0L) * KIB);
    }

    private static Optional<DataCommit> commit(String record) {
        List<String> lines = record.lines().toList();
        String subject = lines.get(0).substring(DATA_PREFIX.length()).strip();
        int changed = (int) lines.stream().skip(1).filter(Predicate.not(String::isBlank)).count();
        try {
            return Optional.of(new DataCommit(LocalDateTime.parse(subject, SUBJECT_TIME), changed));
        } catch (DateTimeParseException unreadable) {
            return Optional.empty();
        }
    }

    private Path repository() {
        return Path.of(publish.getRepoDir()).toAbsolutePath().normalize();
    }

    private String dataPathIn(Path repository) {
        Path data = Path.of(output.getDir()).toAbsolutePath().normalize();
        if (data.startsWith(repository)) {
            return repository.relativize(data).toString();
        }
        throw new SourceFetchException(SOURCE, "the data folder is outside the repository.");
    }

    private String git(Path repository, String... args) {
        Path out = outputFile(args[0]);
        try {
            return run(repository, out, args);
        } finally {
            deleteQuietly(out);
        }
    }

    private String run(Path repository, Path out, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-c", "core.quotepath=off"));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(repository.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(out.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        try {
            Process process = builder.start();
            if (process.waitFor(publish.getCommandTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                requireSuccess(args[0], process.exitValue());
                return Files.readString(out, StandardCharsets.UTF_8);
            }
            process.destroyForcibly();
            throw new SourceFetchException(SOURCE, args[0] + " did not finish within " + publish.getCommandTimeout() + ".");
        } catch (IOException failure) {
            throw new SourceFetchException(SOURCE, args[0] + " could not run (" + failure.getClass().getSimpleName() + ").");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException(SOURCE, args[0] + " was interrupted.");
        }
    }

    private static void requireSuccess(String step, int exit) {
        if (exit != 0) {
            throw new SourceFetchException(SOURCE, step + " failed with exit code " + exit + ".");
        }
    }

    /** 출력은 임시 파일로 받는다 — 파이프가 차서 git 이 멈추는 일 없이 시간 한도를 지킬 수 있다 */
    private static Path outputFile(String step) {
        try {
            return Files.createTempFile("gate-git-", ".out");
        } catch (IOException failure) {
            throw new SourceFetchException(SOURCE, step + " output could not be prepared ("
                    + failure.getClass().getSimpleName() + ").");
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 임시 파일을 지우지 못해도 점검 결과는 그대로다
        }
    }
}
