package com.vulntrend.batch.application.export;

import com.vulntrend.batch.domain.export.CveDetailRepository;
import com.vulntrend.batch.domain.export.WriteOutcome;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사이트 데이터 내보내기(기획서 6장 8번·7장, WBS 4.2). 러너가 요약 단계 다음에 한 번 부른다. 모든 파일이 같은 실행 시각을 본다.
 * - 순서: stats → index → recent → search-index → cve/*.json → meta. meta 의 판 이름이 바뀌어야 프론트가 새 파일을 읽으므로
 *   meta 를 마지막에 쓴다(중간에 실패하면 프론트는 옛 판을 계속 본다)
 * - 상세 파일 생성 범위(P6): index·recent 에 실린 CVE + 점수 상위 1000건. 범위 밖 상세 파일은 지운다(R5 철회 건 포함)
 * - 파일 쓰기 실패는 OutputWriteException 으로 올라간다 — 그 실행은 push 하지 않는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final int DETAIL_TOP = 1000;
    private static final int LIST_FILES = 5;

    private final StatsFileExport stats;
    private final IndexFileExport index;
    private final RecentFileExport recent;
    private final SearchIndexFileExport searchIndex;
    private final CveDetailFileExport details;
    private final MetaFileExport meta;
    private final CveDetailRepository repository;
    private final Clock clock;

    public ExportResult exportAll() {
        Instant now = clock.instant();
        WriteOutcome statsOutcome = stats.write(now);
        Listed indexed = index.write(now);
        Listed recentListed = recent.write(now);
        WriteOutcome searchOutcome = searchIndex.write();
        Set<CveId> scope = new LinkedHashSet<>(indexed.getIds());
        scope.addAll(recentListed.getIds());
        scope.addAll(repository.topIds(DETAIL_TOP));
        DetailFiles detailFiles = details.write(scope, now);
        WriteOutcome metaOutcome = meta.write(now);
        List<WriteOutcome> lists = List.of(statsOutcome, indexed.getOutcome(), recentListed.getOutcome(), searchOutcome,
                metaOutcome);
        int listWritten = (int) lists.stream().filter(outcome -> outcome == WriteOutcome.WRITTEN).count();
        ExportResult result = ExportResult.of(listWritten + detailFiles.getWritten(),
                LIST_FILES - listWritten + detailFiles.getUnchanged(), detailFiles.getRemoved(),
                detailFiles.getWritten() + detailFiles.getUnchanged());
        log.info("Site data exported: {} written, {} unchanged, {} removed, {} detail files.", result.getWritten(),
                result.getUnchanged(), result.getRemoved(), result.getDetailFiles());
        return result;
    }
}
