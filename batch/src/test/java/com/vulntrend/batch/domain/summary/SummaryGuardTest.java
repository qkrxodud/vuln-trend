package com.vulntrend.batch.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("요약에 섞인 명령어 찾기")
class SummaryGuardTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("commands")
    @DisplayName("명령어·스크립트·설정값이 들어 있으면 찾아낸다")
    void findsCommands(String text) {
        assertThat(SummaryGuard.hasCommand(text)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("plainSentences")
    @DisplayName("평범한 안내 문장은 명령어로 보지 않는다")
    void keepsPlainSentences(String text) {
        assertThat(SummaryGuard.hasCommand(text)).isFalse();
    }

    static Stream<Named<String>> commands() {
        return Stream.of(
                named("관리자 권한 실행", "sudo apt-get install --only-upgrade log4j 를 실행한다."),
                named("패키지 설치 명령", "npm install lodash@4.17.21 로 올린다."),
                named("pip 업그레이드", "pip install -U django 로 업데이트한다."),
                named("설정 키=값", "log4j2.formatMsgNoLookups=true 로 설정한다."),
                named("환경 변수", "LOG4J_FORMAT_MSG_NO_LOOKUPS=true 를 넣는다."),
                named("내려받아 실행", "curl -sSL https://example.com/fix.sh 로 고친다."),
                named("셸로 넘기기", "스크립트를 받아 | bash 로 돌린다."),
                named("명령 잇기", "서비스를 멈추고 && 다시 시작한다."),
                named("코드 표시", "`chmod 600` 으로 권한을 줄인다."),
                named("스크립트 태그", "<script>alert(1)</script> 가 실행된다."),
                named("서비스 재시작", "systemctl restart nginx 로 다시 띄운다."));
    }

    static Stream<Named<String>> plainSentences() {
        return Stream.of(
                named("업데이트 안내", "Apache Log4j 2.17.1 이상으로 업데이트한다."),
                named("생태계 이름", "npm 패키지 lodash 를 쓰는 서비스가 영향받는다."),
                named("버전 범위", "Log4j 2.0-beta9 이상 2.15.0 미만 사용자가 영향받는다."),
                named("부등호 범위", "버전 >= 2.0, < 2.15.0 이 영향받는다."),
                named("원문 참고", "원문 참고"),
                named("공격 설명", "공격자가 LDAP 서버를 통해 원격에서 코드를 실행할 수 있다."));
    }
}
