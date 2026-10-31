package com.vulntrend.batch.domain.gate;

import com.vulntrend.batch.domain.gate.dto.DataCommit;
import com.vulntrend.batch.domain.gate.dto.RepoSize;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Gate G "레포 크기 확인, 커밋당 변경 파일 수 기록"(WBS 9장, 성장 추세 기준점). 기록이 목적이라 확인 필요로 남긴다.
 * 500MB 에 닿으면 히스토리를 다시 만들 기준(기획서 P6)이라 실패로 친다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RepoGrowthCheck {

    public static final String ITEM = "레포 크기·커밋당 변경 파일 수";
    private static final long MB = 1024L * 1024L;
    private static final long LIMIT = 500 * MB;
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static GateItem judge(RepoSize size, List<DataCommit> commits) {
        long total = size.packedBytes() + size.looseBytes();
        String repo = "레포 " + megabytes(total, 1);
        List<String> evidence = commits.stream()
                .map(commit -> MINUTE.format(commit.runAt()) + " — " + commit.changedFiles() + "개")
                .toList();
        if (total >= LIMIT) {
            return GateItem.of(ITEM, GateStatus.FAIL, repo + " ≥ 500MB — 히스토리 재생성 기준(P6)", evidence);
        }
        if (commits.isEmpty()) {
            return GateItem.of(ITEM, GateStatus.REVIEW, repo + ", 데이터 커밋 없음", evidence);
        }
        double average = commits.stream().mapToInt(DataCommit::changedFiles).average().orElse(0);
        int max = commits.stream().mapToInt(DataCommit::changedFiles).max().orElse(0);
        return GateItem.of(ITEM, GateStatus.REVIEW, repo + ", 최근 데이터 커밋 " + commits.size() + "개의 바뀐 파일 평균 "
                + String.format(Locale.ROOT, "%.1f", average) + "·최대 " + max + " (기준점 기록)", evidence);
    }

    static String megabytes(long bytes, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "fMB", bytes / (double) MB);
    }
}
