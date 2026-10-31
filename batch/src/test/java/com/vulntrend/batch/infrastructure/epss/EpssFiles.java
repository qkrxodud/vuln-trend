package com.vulntrend.batch.infrastructure.epss;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/** 테스트용 EPSS 점수 파일(gzip CSV)을 만든다 */
final class EpssFiles {

    private EpssFiles() {
    }

    static byte[] gzip(String... lines) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(String.join("\n", lines).concat("\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return bytes.toByteArray();
    }
}
