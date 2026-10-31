package com.vulntrend.batch.infrastructure.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.export.WriteOutcome;
import com.vulntrend.batch.global.config.OutputProperties;
import com.vulntrend.batch.global.error.exception.OutputWriteException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 사이트 데이터 JSON 파일 쓰기(기획서 7장 원칙, WBS 4.1).
 * - 이름은 모든 파일에서 snake_case(G1). 수집 쪽(NVD·KEV 의 camelCase 읽기)을 건드리지 않도록 이 출력에만 쓰는 매퍼를 둔다
 * - 값이 없으면 이름을 남기고 null. 소수는 지수 표기 없이, 날짜는 ISO-8601(오프셋 유지), 맵은 키 순서
 * - 들여쓰기 두 칸·줄바꿈 LF·끝 줄바꿈 하나 — 같은 내용이면 같은 바이트가 나와 git 차이가 작다
 * - 이미 있는 파일과 바이트가 같으면 쓰지 않는다. 다르면 같은 폴더의 임시 파일에 쓴 뒤 한 번에 바꿔 끼운다(반쪽 파일 없음)
 */
@Component
@RequiredArgsConstructor
public class JsonFileWriter implements SiteDataWriter {

    private static final ObjectWriter JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .serializationInclusion(JsonInclude.Include.ALWAYS)
            .build()
            .writer(new DefaultPrettyPrinter(Separators.createDefaultInstance()
                    .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                    .withObjectEmptySeparator("")
                    .withArrayEmptySeparator(""))
                    .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n")));
    private static final byte[] LAST_LINE = "\n".getBytes(StandardCharsets.UTF_8);

    private final OutputProperties properties;

    @Override
    public WriteOutcome write(DataFilePath path, Object document) {
        byte[] content = serialize(path, document);
        Path target = Path.of(properties.getDir()).resolve(path.getValue());
        try {
            if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), content)) {
                return WriteOutcome.UNCHANGED;
            }
            replace(target, content);
            return WriteOutcome.WRITTEN;
        } catch (IOException failure) {
            throw failed(path, failure);
        }
    }

    private static byte[] serialize(DataFilePath path, Object document) {
        try {
            byte[] json = JSON.writeValueAsBytes(document);
            byte[] content = Arrays.copyOf(json, json.length + LAST_LINE.length);
            System.arraycopy(LAST_LINE, 0, content, json.length, LAST_LINE.length);
            return content;
        } catch (JsonProcessingException failure) {
            throw failed(path, failure);
        }
    }

    private static void replace(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".", ".tmp");
        try {
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static OutputWriteException failed(DataFilePath path, Exception failure) {
        return new OutputWriteException(
                "Could not write " + path.getValue() + " (" + failure.getClass().getSimpleName() + ").");
    }
}
