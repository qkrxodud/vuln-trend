package com.vulntrend.batch.application.probe;

// 잡아야 함: 문자 리터럴 '"' 뒤 문자열 속 { 가 괄호 깊이를 흔들지 않아야 중첩 생성자를 본다
public class C14Quote {

    private static final char QUOTE = '"';
    private static final String OPEN = "{";
    private static final String ESCAPED = "a\"{\\";

    static class Seen {
        Seen() {
        }
    }
}
