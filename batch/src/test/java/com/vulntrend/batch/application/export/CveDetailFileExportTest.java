package com.vulntrend.batch.application.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.export.dto.AffectedProductJson;
import com.vulntrend.batch.application.export.dto.CveDetailJson;
import com.vulntrend.batch.application.export.dto.ReferenceLinkJson;
import com.vulntrend.batch.domain.export.dto.AffectedListing;
import com.vulntrend.batch.domain.export.dto.CveDetailListing;
import com.vulntrend.batch.domain.export.dto.ReferenceListing;
import com.vulntrend.batch.domain.product.ProductNames;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.fake.FakeCveDetailRepository;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("cve/CVE-*.json")
class CveDetailFileExportTest {

    private static final ProductNames NAMES = ProductNames.of(List.of());

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    private final FakeSiteDataWriter writer = new FakeSiteDataWriter();

    @Nested
    @DisplayName("상세 한 건은")
    class Detail {

        @Test
        @DisplayName("목록 줄 필드 전부에 원문 설명·한국 시각·KEV 등재일·요약·영향 제품을 더한다")
        void rowPlusDetail() {
            CveDetailJson detail = export(TrendListings.kevDetail("CVE-2026-41203"));

            assertThat(detail.row().score()).isEqualTo(67);
            assertThat(detail.row().whyNow()).isEqualTo("CISA 악용 목록 등재");
            assertThat(detail.description()).isEqualTo("Type confusion in V8 in Google Chrome.");
            assertThat(detail.publishedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 19, 9, 0, 0, 0, ZoneOffset.ofHours(9)));
            assertThat(detail.lastModifiedAt())
                    .isEqualTo(OffsetDateTime.of(2026, 10, 20, 12, 0, 0, 0, ZoneOffset.ofHours(9)));
            assertThat(detail.row().kevAddedAt()).isEqualTo(LocalDate.parse("2026-10-19"));
            assertThat(detail.summary().model()).isEqualTo("claude-haiku-5-5");
            assertThat(detail.summary().generatedAt())
                    .isEqualTo(OffsetDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneOffset.ofHours(9)));
            assertThat(detail.affected()).hasSize(1);
            assertThat(detail.affected().get(0).displayName()).isEqualTo("Chrome");
            assertThat(detail.affected().get(0).vulnerableRange()).isEqualTo("< 130.0.6723.58");
        }

        @Test
        @DisplayName("조치 정보로 CISA 요구 조치 원문과 영향 제품마다 업데이트할 버전을 싣는다")
        void remedy() {
            CveDetailJson detail = export(TrendListings.kevDetail("CVE-2026-41203"));

            assertThat(detail.kevRequiredAction()).isEqualTo("Apply updates per vendor instructions.");
            assertThat(detail.affected().get(0).fixVersions()).containsExactly("130.0.6723.58");
            assertThat(detail.affected().get(0).fixedVersion()).isNull();
        }

        @Test
        @DisplayName("KEV 가 아니면 CISA 요구 조치가 남아 있어도 싣지 않는다")
        void noRequiredActionWithoutKev() {
            assertThat(export(TrendListings.bareDetail("CVE-2026-50001")).kevRequiredAction()).isNull();
        }

        @Test
        @DisplayName("표에 없는 영향 제품은 사람이 읽는 이름으로 싣고 표시 이름은 비워 둔다 — 제조사는 원문 그대로")
        void unmappedAffectedReadable() {
            CveDetailListing stored = TrendListings.kevDetail("CVE-2026-41203");
            CveDetailListing unmapped = new CveDetailListing(stored.row(), stored.description(),
                    stored.lastModifiedAt(), stored.summary(),
                    List.of(new AffectedListing("siemens", "ruggedcom_ape1808_firmware", null, null, null, null, null)),
                    stored.references());

            AffectedProductJson affected = export(unmapped).affected().get(0);

            assertThat(affected.product()).isEqualTo("Ruggedcom APE1808 Firmware");
            assertThat(affected.displayName()).isNull();
            assertThat(affected.slug()).isNull();
            assertThat(affected.vendor()).isEqualTo("siemens");
        }

        @Test
        @DisplayName("참고 링크는 벤더 어드바이저리 다음에 NVD 원문 링크를 붙이고, 같은 주소의 NVD 태그를 원문 그대로 붙인다")
        void vendorThenNvd() {
            CveDetailJson detail = export(TrendListings.kevDetail("CVE-2026-41203"));

            assertThat(detail.references()).containsExactly(
                    new ReferenceLinkJson("https://chromereleases.googleblog.com/x", "vendor", null,
                            List.of("Release Notes", "Vendor Advisory")),
                    new ReferenceLinkJson("https://nvd.nist.gov/vuln/detail/CVE-2026-41203", "nvd", null, List.of()));
        }

        @Test
        @DisplayName("NVD 링크가 이미 저장돼 있으면 하나만 싣는다")
        void storedNvdLinkOnce() {
            CveDetailListing stored = TrendListings.kevDetail("CVE-2026-41203");
            CveDetailListing withNvd = new CveDetailListing(stored.row(), stored.description(),
                    stored.lastModifiedAt(), stored.summary(), stored.affected(),
                    List.of(new ReferenceListing("https://nvd.nist.gov/vuln/detail/CVE-2026-41203", "nvd", null)));

            CveDetailJson detail = export(withNvd);

            assertThat(detail.references()).extracting(ReferenceLinkJson::source).containsExactly("nvd");
        }

        @Test
        @DisplayName("설명·요약이 없으면 설명은 빈 글, 요약은 비워 두고 KEV 가 아니면 등재일도 비운다")
        void bare() {
            CveDetailJson detail = export(TrendListings.bareDetail("CVE-2026-50001"));

            assertThat(detail.description()).isEmpty();
            assertThat(detail.summary()).isNull();
            assertThat(detail.lastModifiedAt()).isNull();
            assertThat(detail.row().kevAddedAt()).isNull();
            assertThat(detail.affected()).isEmpty();
            assertThat(detail.references()).extracting(ReferenceLinkJson::source).containsExactly("nvd");
        }

        private CveDetailJson export(CveDetailListing listing) {
            new CveDetailFileExport(new FakeCveDetailRepository().detail(listing), writer)
                    .write(List.of(listing.row().id()), NOW, NAMES);
            return writer.document("cve/" + listing.row().id().getValue() + ".json", CveDetailJson.class)
                    .orElseThrow();
        }
    }

    @Nested
    @DisplayName("상세 파일 묶음은")
    class Files {

        @Test
        @DisplayName("생성 범위 밖의 상세 파일을 지워 철회되거나 밀려난 CVE 의 파일이 남지 않게 한다")
        void removesOutsideScope() {
            writer.existing("cve/CVE-2026-41203.json").existing("cve/CVE-2021-44228.json")
                    .existing("cve/CVE-2026-99999.json").existing("index.json");
            FakeCveDetailRepository repository = new FakeCveDetailRepository()
                    .detail(TrendListings.kevDetail("CVE-2026-41203"));

            DetailFiles files = new CveDetailFileExport(repository, writer)
                    .write(List.of(CveId.from("CVE-2026-41203")), NOW, NAMES);

            assertThat(writer.removed()).containsExactly("cve/CVE-2021-44228.json", "cve/CVE-2026-99999.json");
            assertThat(writer.files()).containsExactly("cve/CVE-2026-41203.json", "index.json");
            assertThat(files.getWritten()).isEqualTo(1);
            assertThat(files.getRemoved()).isEqualTo(2);
        }

        @Test
        @DisplayName("범위에 있어도 상세를 읽지 못한 CVE 는 파일을 만들지 않고 이미 있으면 지운다")
        void missingDetailRemoved() {
            writer.existing("cve/CVE-2026-50001.json");

            DetailFiles files = new CveDetailFileExport(new FakeCveDetailRepository(), writer)
                    .write(List.of(CveId.from("CVE-2026-50001")), NOW, NAMES);

            assertThat(writer.files()).isEmpty();
            assertThat(files.getRemoved()).isEqualTo(1);
        }

        @Test
        @DisplayName("상세는 500건씩 나눠 읽는다")
        void readsInChunks() {
            FakeCveDetailRepository repository = new FakeCveDetailRepository();
            List<CveId> scope = IntStream.range(0, 1201).mapToObj(i -> CveId.from("CVE-2026-" + (10000 + i))).toList();

            new CveDetailFileExport(repository, writer).write(scope, NOW, NAMES);

            assertThat(repository.askedDetails()).containsExactlyElementsOf(scope);
        }

        @Test
        @DisplayName("같은 내용이면 그대로 둔 수로 센다")
        void unchangedCounted() {
            FakeCveDetailRepository repository = new FakeCveDetailRepository()
                    .detail(TrendListings.kevDetail("CVE-2026-41203"));
            CveDetailFileExport export = new CveDetailFileExport(repository, writer);
            export.write(List.of(CveId.from("CVE-2026-41203")), NOW, NAMES);

            DetailFiles again = export.write(List.of(CveId.from("CVE-2026-41203")), NOW, NAMES);

            assertThat(again.getUnchanged()).isEqualTo(1);
            assertThat(again.getWritten()).isZero();
        }
    }
}
