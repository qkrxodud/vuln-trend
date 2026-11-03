package com.vulntrend.batch.infrastructure.cvelist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.cvelist.CnaAffected;
import com.vulntrend.batch.domain.cvelist.CnaRecord;
import com.vulntrend.batch.domain.cvelist.CnaVersion;
import com.vulntrend.batch.domain.cvelist.CvelistSource;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.global.config.CvelistProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import com.vulntrend.batch.infrastructure.cvelist.dto.AffectedJson;
import com.vulntrend.batch.infrastructure.cvelist.dto.CveRecordJson;
import com.vulntrend.batch.infrastructure.cvelist.dto.VersionJson;
import com.vulntrend.batch.infrastructure.feed.FeedDownloader;
import com.vulntrend.batch.infrastructure.nvd.RequestPause;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CVE.org 공식 CVE Services 읽기 API(cveawg.mitre.org/api/cve/{번호})로 레코드 한 건을 받는다(S3a).
 * GitHub cvelistV5 raw 대신 API 를 쓰는 까닭: 원본이라 늦지 않고(raw 는 저장소 반영·5분 캐시를 거친다), 한도를 머리글로 알려 준다
 * (2026-11-02 실측 RateLimit 25,000/60초), 없는 레코드를 404 로 바로 알려 준다. 열쇠가 필요 없다.
 * 부르기 전마다 requestInterval 만큼 쉰다. 재시도·연결 재사용은 FeedDownloader(5xx·429·연결 실패만 재시도, 404 등은 바로 실패).
 * containers.cna.affected[] 만 읽는다. 이름(vendor·product·packageName)이 하나도 없는 칸은 건너뛴다.
 * 실패 사유에는 응답 본문을 넣지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CvelistClient implements CvelistSource {

    private static final String SOURCE = "cvelist";

    private final CvelistProperties properties;
    private final ObjectMapper objectMapper;
    private final RequestPause pause;

    @Override
    public CnaRecord fetch(CveId cveId) {
        waitBeforeRequest();
        byte[] body = FeedDownloader.of(SOURCE, properties.getBaseUrl() + "/" + cveId.getValue(),
                properties.getMaxAttempts(), properties.getRetryBackoff(), properties.getRequestTimeout(), pause)
                .download();
        return CnaRecord.of(cveId, affectedOf(read(body)));
    }

    private void waitBeforeRequest() {
        try {
            pause.pause(properties.getRequestInterval());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException(SOURCE, "interrupted while waiting between requests.");
        }
    }

    private CveRecordJson read(byte[] body) {
        try {
            return objectMapper.readValue(body, CveRecordJson.class);
        } catch (IOException unreadable) {
            throw new SourceFetchException(SOURCE, "unreadable record (" + unreadable.getClass().getSimpleName()
                    + ").");
        }
    }

    private static List<CnaAffected> affectedOf(CveRecordJson record) {
        List<AffectedJson> affected = Optional.ofNullable(record.containers())
                .map(containers -> containers.cna())
                .map(cna -> cna.affected())
                .orElse(List.of());
        return affected.stream()
                .filter(Objects::nonNull)
                .filter(CvelistClient::hasName)
                .map(CvelistClient::toAffected)
                .toList();
    }

    private static boolean hasName(AffectedJson entry) {
        return Objects.nonNull(entry.vendor()) || Objects.nonNull(entry.product())
                || Objects.nonNull(entry.packageName());
    }

    private static CnaAffected toAffected(AffectedJson entry) {
        List<CnaVersion> versions = Optional.ofNullable(entry.versions()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .map(CvelistClient::toVersion)
                .toList();
        List<String> cpes = Optional.ofNullable(entry.cpes()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .toList();
        return CnaAffected.of(entry.vendor(), entry.product(), entry.packageName(), entry.defaultStatus(), versions,
                cpes, entry.packageUrl());
    }

    private static CnaVersion toVersion(VersionJson version) {
        return CnaVersion.of(version.version(), version.status(), version.lessThan(), version.lessThanOrEqual(),
                version.versionType());
    }
}
