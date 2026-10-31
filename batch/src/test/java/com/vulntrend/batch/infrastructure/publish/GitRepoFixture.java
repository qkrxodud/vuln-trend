package com.vulntrend.batch.infrastructure.publish;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 시험용 git 저장소 한 쌍: 원격 역할의 bare 저장소와 그것을 clone 한 작업 저장소. 둘 다 JUnit 임시 폴더 안에만 만든다.
 * 실제 레포의 git 설정·원격은 건드리지 않는다.
 */
public class GitRepoFixture {

    public static final String DATA = "web/public/data";

    private final Path remote;
    private final Path work;

    private GitRepoFixture(Path remote, Path work) {
        this.remote = remote;
        this.work = work;
    }

    public static GitRepoFixture create(Path root) throws IOException {
        Path remote = root.resolve("remote.git");
        Path work = root.resolve("work");
        git(root, "init", "-q", "--bare", "-b", "main", remote.toString());
        git(root, "clone", "-q", remote.toString(), work.toString());
        git(work, "config", "user.email", "batch@example.com");
        git(work, "config", "user.name", "vuln-trend batch");
        git(work, "checkout", "-q", "-b", "main");
        GitRepoFixture fixture = new GitRepoFixture(remote, work);
        fixture.write(DATA + "/meta.json", "{\"version\": 1}\n");
        fixture.write("web/src/App.tsx", "export const App = 1\n");
        git(work, "add", "-A");
        git(work, "commit", "-q", "-m", "init");
        git(work, "push", "-q", "origin", "HEAD");
        return fixture;
    }

    public Path work() {
        return work;
    }

    public Path dataDir() {
        return work.resolve(DATA);
    }

    public void write(String relative, String content) throws IOException {
        Path file = work.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    public void delete(String relative) throws IOException {
        Files.delete(work.resolve(relative));
    }

    public void stage(String relative) throws IOException {
        git(work, "add", "--", relative);
    }

    public void setRemoteUrl(String url) throws IOException {
        git(work, "remote", "set-url", "origin", url);
    }

    /** 원격 저장소의 커밋 제목들(최신 먼저) */
    public List<String> remoteSubjects() throws IOException {
        return lines(git(remote, "log", "--format=%s", "main"));
    }

    /** 원격 최신 커밋이 바꾼 파일들 */
    public List<String> remoteLastCommitFiles() throws IOException {
        return lines(git(remote, "show", "--name-only", "--format=", "main"));
    }

    /** 작업 저장소에서 아직 커밋하지 않고 올려 둔(staged) 파일들 */
    public List<String> stagedFiles() throws IOException {
        return lines(git(work, "diff", "--cached", "--name-only"));
    }

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            lines.add(line.trim());
        }
        return lines;
    }

    private static String git(Path dir, String... args) throws IOException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) {
                return out;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new IOException("git " + String.join(" ", args) + " failed: " + out);
    }
}
