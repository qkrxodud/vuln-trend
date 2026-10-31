package com.vulntrend.batch.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 출력 JSON 과 프론트 목업의 모양 대조. 목업은 test/resources/contract/ 에 고정한 사본이다 — web/public/data 는 첫 하루 실행(bbe0756)부터
 * 배치가 매일 바꾸는 산출물이라 대조 기준이 될 수 없다. 사본은 bbe0756^ 의 web/public/data(G1 계약으로 만든 목업) 6종에서 꺼냈다.
 * 프론트 계약(web/src/types/*.ts)이 바뀌면 이 사본도 같이 바꾼다.
 * 이름과 순서, 값의 종류(문자·숫자·참거짓·배열·객체)가
 * 같아야 한다. 계약상 비어 있을 수 있는 값은 어느 한쪽이 null 이면 종류를 따지지 않는다. 배열은 모든 원소를 목업 첫 원소와 대조한다.
 */
final class MockShapes {

    private static final String MOCK_DIR = "/contract/";
    private static final ObjectMapper JSON = new ObjectMapper();

    private MockShapes() {
    }

    static void assertSameShape(Path written, String mockFile) throws IOException {
        JsonNode actual = JSON.readTree(written.toFile());
        JsonNode mock;
        try (InputStream in = MockShapes.class.getResourceAsStream(MOCK_DIR + mockFile)) {
            assertThat(in).as("고정한 목업 " + mockFile).isNotNull();
            mock = JSON.readTree(in);
        }
        compare("$", actual, mock);
    }

    private static void compare(String where, JsonNode actual, JsonNode mock) {
        if (actual.isNull() || mock.isNull()) {
            return;
        }
        assertThat(actual.getNodeType()).as(where).isEqualTo(mock.getNodeType());
        if (mock.isObject()) {
            assertThat(names(actual)).as(where).containsExactlyElementsOf(names(mock));
            names(mock).forEach(name -> compare(where + "." + name, actual.get(name), mock.get(name)));
        }
        if (mock.isArray() && mock.size() > 0) {
            assertThat(actual.size()).as(where + " 원소 수").isPositive();
            for (int i = 0; i < actual.size(); i++) {
                compare(where + "[" + i + "]", actual.get(i), mock.get(0));
            }
        }
    }

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
