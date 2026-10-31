package com.vulntrend.batch.infrastructure.nvd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 테스트용 NVD cve JSON. Log4Shell 은 실응답(2026-10-30 수집) 원본, 나머지는 같은 모양으로 만든 것 */
public final class NvdSamples {

    /** 분석 끝, NVD 3.1 점수, CPE 2개(하나는 영향 없음), 제조사 안내문 링크 1개 */
    public static final String CHROME = """
            {"id":"CVE-2026-30001","vulnStatus":"Analyzed","published":"2026-10-29T18:15:00.000",
             "lastModified":"2026-10-30T02:00:00.000",
             "descriptions":[{"lang":"en","value":"Heap overflow in V8 in Google Chrome before 130.0.6723.69. More."}],
             "metrics":{"cvssMetricV31":[{"source":"nvd@nist.gov","type":"Primary","cvssData":{"version":"3.1","baseScore":8.8}}]},
             "configurations":[{"nodes":[{"cpeMatch":[
               {"vulnerable":true,"criteria":"cpe:2.3:a:google:chrome:*:*:*:*:*:*:*:*","versionEndExcluding":"130.0.6723.69"},
               {"vulnerable":false,"criteria":"cpe:2.3:o:microsoft:windows:-:*:*:*:*:*:*:*"}]}]}],
             "references":[{"url":"https://chromereleases.googleblog.com/2026/10/stable.html","tags":["Vendor Advisory"]},
                           {"url":"https://example.com/writeup","tags":["Third Party Advisory"]}]}
            """;

    /** 분석 전, 점수 없음 */
    public static final String WORDPRESS = """
            {"id":"CVE-2026-30002","vulnStatus":"Awaiting Analysis","published":"2026-10-30T08:15:00.000",
             "lastModified":"2026-10-30T08:15:00.000",
             "descriptions":[{"lang":"en","value":"Stored XSS in the Foo plugin for WordPress."}],"metrics":{}}
            """;

    /** NVD 가 철회한 CVE */
    public static final String REJECTED = """
            {"id":"CVE-2026-30003","vulnStatus":"Rejected","published":"2026-10-28T08:15:00.000",
             "lastModified":"2026-10-30T09:00:00.000",
             "descriptions":[{"lang":"en","value":"Rejected reason: duplicate of CVE-2026-30001."}]}
            """;

    private NvdSamples() {
    }

    public static String log4shell() {
        try (InputStream in = NvdSamples.class.getResourceAsStream("/nvd/CVE-2021-44228.json")) {
            String page = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int start = page.indexOf("\"cve\":") + "\"cve\":".length();
            int end = page.lastIndexOf("}]}");
            return page.substring(start, end);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
