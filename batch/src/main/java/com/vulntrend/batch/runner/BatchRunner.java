package com.vulntrend.batch.runner;

import com.vulntrend.batch.application.category.AttackTypeAssignService;
import com.vulntrend.batch.application.collect.CollectOutcome;
import com.vulntrend.batch.application.collect.CollectService;
import com.vulntrend.batch.application.export.ExportResult;
import com.vulntrend.batch.application.export.ExportService;
import com.vulntrend.batch.application.gate.GateCheckService;
import com.vulntrend.batch.application.product.ProductAssignService;
import com.vulntrend.batch.application.publish.SitePublishService;
import com.vulntrend.batch.application.score.ScoreService;
import com.vulntrend.batch.application.stat.DailyStatService;
import com.vulntrend.batch.application.summary.SummaryService;
import com.vulntrend.batch.application.vulnerability.LegacyTitleService;
import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.gate.GateReport;
import com.vulntrend.batch.domain.publish.PublishDecision;
import com.vulntrend.batch.global.error.exception.CollectionFailedException;
import com.vulntrend.batch.global.error.exception.GateCheckFailedException;
import com.vulntrend.batch.global.error.exception.OutputWriteException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 정기 실행(launchd 가 2시간마다 부름)의 진입점. 단계 순서와 실패 규칙(기획서 6장)을 적용한다.
 * 수집(1~4) → 하나라도 실패하면 여기서 멈추고 실패한 실행으로 끝낸다(CollectionFailedException)
 *   → 대표 제품(백엔드 B 2.3) → 공격 유형(약점 번호로, 사이트 분류) → 점수(5) → 일별 통계(6, 최근 14일) → 옛 규칙 제목 다시 계산(B1, 바뀔 것이 없으면 바로 끝)
 *   → 한국어 요약(7, 비치명 — 실패해도 다음 단계로 간다. 제목 재계산 뒤라야 요약 제목이 올라간다)
 *   → 게시 판정(보이는 지문·마지막 게시, 사용자 승인 2026-11-01) → 사이트 데이터 파일(8, 백엔드 B 4.2) → 게시(9, git push).
 * 게시 판정: 그 한국 날짜의 첫 게시이거나 보이는 지문이 마지막 게시와 다를 때만 파일을 쓰고 올린다. 둘 다 아니면 파일을 쓰지도
 * 올리지도 않고 정상(종료 코드 0)으로 끝난다. 검색 색인은 그날 첫 게시에서만 다시 만든다.
 * 데이터 파일을 쓰지 못하면(OutputWriteException) 게시하지 않는다 — 반쪽 데이터를 올리지 않는다.
 * 실패한 실행은 배치 예외를 밖으로 던지고, Spring 이 종료 코드 1 로 프로세스를 끝낸다(VulnTrendException 이 종료 코드를 정함, WBS 4.3).
 * 러너 빈은 실행마다 바뀌는 값을 들고 있지 않는다 — 결과는 지역 변수와 예외로만 다룬다.
 * 소스별 진행 기록은 CollectService 가 따로 남긴다 — 한 소스가 실패해도 다른 소스 커서는 전진한다.
 * --backfill-days=N 이면 지난 N일 NVD 만 한 번 가져오고 끝낸다(WBS 1.5). 백필은 게시하지 않는다.
 * --gate-check 면 Gate G 배치 점검 표만 남긴다(읽기만). 실패 항목이 있으면 종료 코드 1(GateCheckFailedException).
 * batch.runner.enabled=false 면 등록하지 않는다 — 테스트 프로필이 실제 NVD 를 부르지 않게 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "batch.runner", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class BatchRunner implements CommandLineRunner {

    private final CollectService collect;
    private final ProductAssignService productAssignment;
    private final AttackTypeAssignService attackTypes;
    private final ScoreService score;
    private final DailyStatService dailyStat;
    private final LegacyTitleService legacyTitles;
    private final SummaryService summary;
    private final ExportService export;
    private final SitePublishService publishing;
    private final GateCheckService gate;

    @Override
    public void run(String... args) {
        RunMode mode = RunMode.from(args);
        if (mode.isGateCheck()) {
            gateCheck();
            return;
        }
        if (mode.isBackfill()) {
            backfill(mode.getBackfillSpan());
            return;
        }
        CollectOutcome collected = collect.collectAll();
        if (collected.hasFailure()) {
            log.warn("Collection failed for {}; scoring and publishing are skipped this run.", collected.failedSources());
            throw new CollectionFailedException("Failed sources: " + collected.failedSources() + ".");
        }
        log.info("Collection finished for {} sources.", collected.getResults().size());
        productAssignment.assignAll();
        attackTypes.assignAll();
        scoreAndPublish();
    }

    private void scoreAndPublish() {
        score.rescoreAll();
        dailyStat.recalculateRecent();
        legacyTitles.renewAll();
        summary.summarizeTop();
        PublishDecision decision = publishing.decide(export.visibleFingerprint());
        if (decision.isSkipped()) {
            log.info("No visible change since the last publish today; site data is not written or pushed this run.");
            return;
        }
        log.info("Publishing this run ({}).", decision);
        ExportResult exported = exportSiteData(decision.rebuildsSearchIndex());
        publishing.publish(exported.getFingerprint());
    }

    // 파일을 다 쓰지 못한 실행은 게시하지 않고 실패로 끝낸다(문장은 우리 예외의 고정 문구 + 상대 경로뿐)
    private ExportResult exportSiteData(boolean rebuildSearchIndex) {
        try {
            return export.exportAll(rebuildSearchIndex);
        } catch (OutputWriteException e) {
            log.warn("Site data could not be written ({}); nothing is pushed this run.", e.getMessage());
            throw e;
        }
    }

    // 점검 표는 웹 gate:check 와 같은 모양(| 항목 | 결과 | 근거 |)으로 그대로 남긴다
    private void gateCheck() {
        GateReport report = gate.check();
        log.info("Gate G batch check\n{}", report.render());
        if (report.hasFailure()) {
            throw new GateCheckFailedException("See the failing items in the table above.");
        }
    }

    // 1회성 실행: NVD 만 가져오고 점수·게시 단계로 넘어가지 않는다. 다음 정기 실행이 이어서 점수를 매긴다
    private void backfill(Duration span) {
        CollectResult result = collect.backfill(SourceName.NVD, span);
        if (result.isFailed()) {
            log.warn("Backfill of NVD for the last {} days failed; run it again to retry the same period.", span.toDays());
            throw new CollectionFailedException("NVD backfill of the last " + span.toDays() + " days.");
        }
        log.info("Backfill of NVD for the last {} days loaded {} records; scoring waits for the next daily run.",
                span.toDays(), result.getLoadedCount());
    }
}
