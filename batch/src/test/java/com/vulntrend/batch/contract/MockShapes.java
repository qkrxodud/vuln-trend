package com.vulntrend.batch.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 출력 JSON 과 프론트 목업(web/public/data, G1 계약으로 만든 파일)의 모양 대조. 이름과 순서, 값의 종류(문자·숫자·참거짓·배열·객체)가
 * 같아야 한다. 계약상 비어 있을 수 있는 값은 어느 한쪽이 null 이면 종류를 따지지 않는다. 배열은 모든 원소를 목업 첫 원소와 대조한다.
 */
final class MockShapes {

    static final Path MOCK_DIR = Path.of("..", "web", "public", "data");
    private static final ObjectMapper JSON = new ObjectMapper();

    private MockShapes() {
    }

    static void assertSameShape(Path written, String mockFile) throws IOException {
        JsonNode actual = JSON.readTree(written.toFile());
        JsonNode mock = JSON.readTree(MOCK_DIR.resolve(mockFile).toFile());
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
