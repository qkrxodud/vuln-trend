package com.vulntrend.batch.domain.product;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 제품군(사용자 결정 2026-11-01, 키·이름·순서 확정). 출력 순서가 이 순서다. 키는 vulnerability.product_family 와
 * 사이트 데이터의 product_family 에 그대로 들어간다
 */
@Getter
@RequiredArgsConstructor
public enum ProductFamily {

    OS("os", "운영체제"),
    BROWSER("browser", "브라우저"),
    NETWORK("network", "네트워크·보안 장비"),
    WEB_CMS("web_cms", "웹 CMS·플러그인"),
    SERVER("server", "서버·DB·미들웨어"),
    DEV("dev", "개발 라이브러리·도구"),
    CLOUD("cloud", "클라우드·가상화"),
    ICS_IOT("ics_iot", "산업제어·IoT·펌웨어"),
    MOBILE("mobile", "모바일"),
    DESKTOP("desktop", "업무용·데스크톱 앱");

    private final String key;
    private final String koreanName;

    public static Optional<ProductFamily> fromKey(String key) {
        return Arrays.stream(values()).filter(family -> family.key.equals(key)).findFirst();
    }
}
