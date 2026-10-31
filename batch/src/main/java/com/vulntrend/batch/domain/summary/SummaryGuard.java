package com.vulntrend.batch.domain.summary;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 요약에 명령어·스크립트·설정값이 섞였는지 본다(기획서 6장 요약 가드). 섞였으면 그 요약은 버리고 다시 만들지 않는다.
 * 사이트에 그대로 실리는 글이라 따라 하면 위험한 문장을 막는 마지막 관문이다. 놓친 모양이 나오면 여기에 더하고 테스트에 예시를 넣는다.
 * 원문에 없는 버전(WBS 6.3): "2.17.1" 처럼 마침표로 이은 숫자가 원문(설명·제품·범위·고친 버전)에 없으면 지어낸 것으로 본다.
 * 판정 기준은 프론트 gate:check(web/scripts/gate/static-checks.ts 의 VERSION·sourceTextOf·scoreTexts)와 같다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SummaryGuard {

    private static final List<Pattern> COMMAND_PATTERNS = List.of(
            // 코드 표시·스크립트 시작
            Pattern.compile("`|<script|<\\?php|#!/", Pattern.CASE_INSENSITIVE),
            // 관리자 권한 실행
            Pattern.compile("\\bsudo\\s", Pattern.CASE_INSENSITIVE),
            // 패키지·서비스 도구 + 하위 명령이나 옵션 ("npm 패키지" 같은 생태계 이름은 걸리지 않게)
            Pattern.compile("\\b(apt-get|apt|yum|dnf|brew|npm|npx|yarn|pnpm|pip3?|gem|composer|mvn|gradle|docker"
                    + "|kubectl|systemctl|service|git)\\s+(install|update|upgrade|remove|uninstall|add|run|exec|pull"
                    + "|restart|stop|start|apply|set|config|-{1,2}\\w)", Pattern.CASE_INSENSITIVE),
            // 내려받기·권한·방화벽·레지스트리 도구 + 인자
            Pattern.compile("\\b(curl|wget|chmod|chown|iptables|powershell|reg)\\s+(-|https?://|\\d|add\\b|delete\\b)",
                    Pattern.CASE_INSENSITIVE),
            // 명령 잇기·셸로 넘기기
            Pattern.compile("&&|\\|\\s*(sh|bash|zsh)\\b"),
            // 설정 키=값, 환경 변수 (">= 2.0" 같은 범위는 이름 바로 뒤에 = 가 오지 않으므로 걸리지 않는다)
            Pattern.compile("[A-Za-z_][A-Za-z0-9_.\\-]*\\s*=\\s*[^\\s=]"));

    // 앞에 숫자·마침표가 없고, 뒤에 숫자가 이어지지 않는 "숫자.숫자(.숫자…)". 앞의 v 는 떼고 본다
    private static final Pattern VERSION = Pattern.compile("(?<![\\d.])v?(\\d+(?:\\.\\d+)+)(?!\\d)");

    public static boolean hasCommand(String text) {
        return COMMAND_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }

    /** 원문에 없는 버전 숫자가 있는가. CVSS 점수(9.8)는 버전으로 보지 않는다 */
    public static boolean hasUnknownVersion(String text, SummarySource source) {
        String sourceText = source.sourceText();
        List<String> scores = source.scoreTexts();
        Matcher matcher = VERSION.matcher(text);
        while (matcher.find()) {
            String version = matcher.group(1);
            if (isUnknown(version, sourceText, scores)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnknown(String version, String sourceText, List<String> scores) {
        return Stream.of(sourceText.contains(version), scores.contains(version)).noneMatch(Boolean::booleanValue);
    }
}
