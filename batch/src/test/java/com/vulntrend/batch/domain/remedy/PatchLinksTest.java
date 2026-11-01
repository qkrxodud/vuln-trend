package com.vulntrend.batch.domain.remedy;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.remedy.dto.TaggedLink;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("패치·제조사 공지 링크")
class PatchLinksTest {

    private static TaggedLink link(String url, String... tags) {
        return new TaggedLink(url, List.of(tags));
    }

    @Nested
    @DisplayName("한 줄 조치에 쓸 첫 링크는")
    class First {

        @Test
        @DisplayName("패치 → 제조사 공지 → 완화 방법 → 출시 노트 순으로 고른다")
        void byTagPriority() {
            List<TaggedLink> links = List.of(link("https://a/notes", "Release Notes"),
                    link("https://a/mitigation", "Mitigation"), link("https://a/advisory", "Vendor Advisory"),
                    link("https://a/patch", "Patch"));

            assertThat(PatchLinks.first(links)).contains("https://a/patch");
            assertThat(PatchLinks.first(links.subList(0, 3))).contains("https://a/advisory");
            assertThat(PatchLinks.first(links.subList(0, 2))).contains("https://a/mitigation");
            assertThat(PatchLinks.first(links.subList(0, 1))).contains("https://a/notes");
        }

        @Test
        @DisplayName("같은 순위면 원본에 먼저 나온 링크를 고른다")
        void firstInSourceOrder() {
            assertThat(PatchLinks.first(List.of(link("https://a/1", "Vendor Advisory"),
                    link("https://a/2", "Release Notes", "Vendor Advisory")))).contains("https://a/1");
        }

        @Test
        @DisplayName("끊긴 링크 표시가 있으면 고르지 않는다")
        void skipsBrokenLink() {
            assertThat(PatchLinks.first(List.of(link("https://a/broken", "Patch", "Broken Link"),
                    link("https://a/advisory", "Vendor Advisory")))).contains("https://a/advisory");
        }

        @Test
        @DisplayName("조치 태그가 붙은 링크가 없으면 없다")
        void none() {
            assertThat(PatchLinks.first(List.of(link("https://a/x", "Third Party Advisory"), link("https://a/y"))))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("주소가 같은 NVD 링크의 태그를 원문 그대로 찾고, 없으면 빈 목록이다")
    void tagsOfUrl() {
        List<TaggedLink> links = List.of(link("https://a/advisory", "Release Notes", "Vendor Advisory"));

        assertThat(PatchLinks.tagsOf("https://a/advisory", links)).containsExactly("Release Notes", "Vendor Advisory");
        assertThat(PatchLinks.tagsOf("https://news.example/x", links)).isEmpty();
    }
}
