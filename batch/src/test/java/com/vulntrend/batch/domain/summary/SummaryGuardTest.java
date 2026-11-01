package com.vulntrend.batch.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("요약에 섞인 명령어·지어낸 버전 찾기")
class SummaryGuardTest {

    private static final SummarySource LOG4SHELL = SummarySource.of(CveId.from("CVE-2021-44228"),
            "Apache Log4j2 2.0-beta9 through 2.15.0 JNDI features do not protect against attacker controlled LDAP.",
            List.of(SummaryProduct.of("apache", "log4j", ">= 2.0-beta9, < 2.15.0", "2.15.1"),
                    SummaryProduct.of("apache", "log4j-core-2.16", null, null)),
            new BigDecimal("10.0"));

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

    @Nested
    @DisplayName("걸린 값")
    class Found {

        @Test
        @DisplayName("명령어가 걸리면 걸린 자리부터 그 낱말 끝까지를 돌려준다")
        void commandFragment() {
            assertThat(SummaryGuard.commandIn("먼저 log4j2.formatMsgNoLookups=true 로 설정한 뒤 다시 시작한다."))
                    .hasValue("log4j2.formatMsgNoLookups=true");
            assertThat(SummaryGuard.commandIn("curl -sSL https://example.com/fix.sh 로 고친다.")).hasValue("curl -sSL");
        }

        @Test
        @DisplayName("원문에 없는 버전이 걸리면 그 버전을 돌려준다")
        void unknownVersion() {
            assertThat(SummaryGuard.unknownVersionIn("Log4j 2.15.0 미만이며 2.17.1 이상으로 올린다.", LOG4SHELL))
                    .hasValue("2.17.1");
        }

        @Test
        @DisplayName("걸린 것이 없으면 아무것도 돌려주지 않는다")
        void nothing() {
            assertThat(SummaryGuard.commandIn("원문 참고")).isEmpty();
            assertThat(SummaryGuard.unknownVersionIn("Log4j 2.15.0 이상으로 올린다.", LOG4SHELL)).isEmpty();
        }
    }

    @Nested
    @DisplayName("원문 버전을 줄여 쓴 모양")
    class ShortenedVersions {

        private final SummarySource php = SummarySource.of(CveId.from("CVE-2026-109618"), "PHP uses longjmp().",
                List.of(SummaryProduct.of("php", "php", ">=7.3.0, <8.2.35 || >=8.3.0, <8.3.36", null)),
                new BigDecimal("9.8"));

        @Test
        @DisplayName("원문 7.3.0 을 7.3 으로 줄여 쓰면 지어낸 버전으로 보지 않는다")
        void shortenedVersion() {
            assertThat(SummaryGuard.hasUnknownVersion("PHP 7.3 이상 8.2.35 미만이 영향받는다.", php)).isFalse();
        }

        @Test
        @DisplayName("8.x 같은 계열 표기는 버전 숫자로 보지 않는다")
        void seriesNotation() {
            assertThat(SummaryGuard.hasUnknownVersion("PHP 8.x 계열 사용자가 영향받는다.", php)).isFalse();
        }

        @Test
        @DisplayName("범위 끝에서 하나를 뺀 버전처럼 원문 숫자로 계산한 버전은 지어낸 것으로 본다")
        void derivedVersionIsUnknown() {
            assertThat(SummaryGuard.unknownVersionIn("PHP 8.2.34 이하가 영향받는다.", php)).hasValue("8.2.34");
        }

        @Test
        @DisplayName("원문 범위의 = 기호를 제품 이름 뒤에 그대로 옮기면 설정값으로 보고 버린다(프론트 점검과 같은 기준)")
        void copiedEqualsSignIsCommand() {
            assertThat(SummaryGuard.commandIn("Ivanti Connect Secure =22.8-r2 가 영향받는다.")).isPresent();
        }
    }

    @Nested
    @DisplayName("원문에 없는 버전")
    class Versions {

        @Test
        @DisplayName("원문 어디에도 없는 버전이 나오면 지어낸 것으로 본다")
        void inventedVersion() {
            assertThat(SummaryGuard.hasUnknownVersion("Log4j 2.17.1 이상으로 업데이트한다.", LOG4SHELL)).isTrue();
        }

        @Test
        @DisplayName("앞에 v 를 붙여도 같은 버전으로 본다")
        void prefixedVersion() {
            assertThat(SummaryGuard.hasUnknownVersion("v2.17.1 이상으로 업데이트한다.", LOG4SHELL)).isTrue();
            assertThat(SummaryGuard.hasUnknownVersion("v2.15.0 미만이 영향받는다.", LOG4SHELL)).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.vulntrend.batch.domain.summary.SummaryGuardTest#knownVersions")
        @DisplayName("원문에 있는 버전은 통과한다")
        void knownVersion(String text) {
            assertThat(SummaryGuard.hasUnknownVersion(text, LOG4SHELL)).isFalse();
        }

        @Test
        @DisplayName("CVSS 점수는 버전으로 보지 않는다")
        void scoreIsNotVersion() {
            assertThat(SummaryGuard.hasUnknownVersion("심각도 점수 10.0 의 원격 코드 실행 취약점이다.", LOG4SHELL)).isFalse();
        }

        @Test
        @DisplayName("점수가 없는 CVE 에서 원문에 없는 숫자는 점수로 봐주지 않는다")
        void noScore() {
            SummarySource unrated = SummarySource.of(CveId.from("CVE-2026-0001"), "A flaw in Foo.", List.of(), null);

            assertThat(SummaryGuard.hasUnknownVersion("점수 9.8 의 취약점이다.", unrated)).isTrue();
        }

        @Test
        @DisplayName("버전 숫자가 없는 문장은 그대로 통과한다")
        void noVersion() {
            assertThat(SummaryGuard.hasUnknownVersion("원문 참고", LOG4SHELL)).isFalse();
        }
    }

    static Stream<Named<String>> knownVersions() {
        return Stream.of(
                named("설명에 있는 버전", "Log4j 2.0-beta9 이상 2.15.0 이하가 영향받는다."),
                named("영향 범위에 있는 버전", "2.15.0 미만 사용자가 영향받는다."),
                named("고친 버전", "Log4j 2.15.1 이상으로 업데이트한다."),
                named("제품 이름에 든 버전", "log4j-core-2.16 사용자도 확인한다."));
    }
}
