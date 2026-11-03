package com.vulntrend.batch.support;

import com.vulntrend.batch.application.export.CategoriesFileExport;
import com.vulntrend.batch.application.export.CveDetailFileExport;
import com.vulntrend.batch.application.export.ExportService;
import com.vulntrend.batch.application.export.IndexFileExport;
import com.vulntrend.batch.application.export.MetaFileExport;
import com.vulntrend.batch.application.export.RecentFileExport;
import com.vulntrend.batch.application.export.SearchIndexFileExport;
import com.vulntrend.batch.application.export.StatsFileExport;
import com.vulntrend.batch.application.score.ScoreService;
import com.vulntrend.batch.application.stat.KpiService;
import com.vulntrend.batch.fake.FakeCategoryRepository;
import com.vulntrend.batch.fake.FakeCveDetailRepository;
import com.vulntrend.batch.fake.FakeDailyStatRepository;
import com.vulntrend.batch.fake.FakeEpssRepository;
import com.vulntrend.batch.fake.FakeKpiRepository;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import com.vulntrend.batch.fake.FakeTrendListRepository;
import com.vulntrend.batch.fake.FakeTrendScoreRepository;
import java.time.Clock;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 러너 시험용 실제 내보내기: 빈 가짜 저장소에서 읽어 가짜 파일 쓰기로 쓴다(백엔드 B 의 ExportServiceTest 와 같은 조립) */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SiteExports {

    public static ExportService writingTo(FakeSiteDataWriter writer, Clock clock) {
        FakeTrendListRepository trends = new FakeTrendListRepository();
        FakeCveDetailRepository details = new FakeCveDetailRepository();
        return new ExportService(new StatsFileExport(new FakeDailyStatRepository(), writer),
                new IndexFileExport(trends, writer), new RecentFileExport(trends, writer),
                new SearchIndexFileExport(details, writer),
                new CategoriesFileExport(trends, new FakeCategoryRepository(), writer),
                new CveDetailFileExport(details, writer),
                new MetaFileExport(new KpiService(new FakeKpiRepository()), writer), details,
                new ScoreService(new FakeTrendScoreRepository(0), new FakeEpssRepository(Set.of()), clock), clock);
    }
}
