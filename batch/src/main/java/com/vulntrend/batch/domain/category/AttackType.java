package com.vulntrend.batch.domain.category;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 공격 유형(사이트 분류, 사용자 결정 2026-11-01). 키는 출력 JSON·프론트가 같이 쓰는 값이라 바꾸지 않는다.
 * 선언 순서가 출력 순서다. 어떤 약점 번호(CWE)가 어느 유형인지는 AttackTypes 의 표에 있다.
 */
@Getter
@RequiredArgsConstructor
public enum AttackType {
    RCE("rce", "원격 코드 실행"),
    INJECTION("injection", "주입(SQL·명령)"),
    XSS("xss", "크로스 사이트 스크립팅"),
    AUTH("auth", "인증·권한 우회"),
    INFO("info", "정보 노출"),
    MEMORY("memory", "메모리 손상"),
    DOS("dos", "서비스 거부"),
    PATH("path", "경로 조작·파일 업로드"),
    REQUEST_FORGERY("request_forgery", "요청 위조(CSRF·SSRF)"),
    OTHER("other", "기타");

    private final String key;
    private final String koreanName;
}
