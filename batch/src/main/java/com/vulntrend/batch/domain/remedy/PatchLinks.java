package com.vulntrend.batch.domain.remedy;

import com.vulntrend.batch.domain.remedy.dto.TaggedLink;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 패치·제조사 공지 링크(사용자 피드백 "조치 방법이 없다", 승인 2026-11-01). NVD 원본 참고 링크의 태그로 고른다.
 * 한 줄 조치에 쓸 첫 링크: Patch → Vendor Advisory → Mitigation → Release Notes 순, 같은 순위면 원본 순서.
 * "Broken Link" 태그가 붙은 링크는 고르지 않는다
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PatchLinks {

    private static final List<String> PRIORITY = List.of("Patch", "Vendor Advisory", "Mitigation", "Release Notes");
    private static final String BROKEN = "Broken Link";

    public static Optional<String> first(List<TaggedLink> links) {
        List<TaggedLink> working = links.stream().filter(link -> link.tags().stream().noneMatch(BROKEN::equals))
                .toList();
        return PRIORITY.stream()
                .flatMap(tag -> working.stream().filter(link -> link.tags().contains(tag)).findFirst().stream())
                .map(TaggedLink::url)
                .findFirst();
    }

    /** 주소가 같은 NVD 링크의 태그 원문. 없으면 빈 목록 */
    public static List<String> tagsOf(String url, List<TaggedLink> links) {
        return links.stream().filter(link -> link.url().equals(url)).findFirst().map(TaggedLink::tags)
                .orElse(List.of());
    }
}
