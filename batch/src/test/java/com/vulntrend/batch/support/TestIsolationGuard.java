package com.vulntrend.batch.support;

import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 시험 안전장치(2026-11-01, 공유 폴더 시험이 실제 .env 를 읽어 GitHub push·실제 요약 호출을 시도한 일 뒤).
 * 시험 classpath 의 META-INF/spring.factories 로 모든 시험 Spring 컨텍스트에 붙는다. 실제 같은 값이 보이면 컨텍스트를 시작하지 않는다:
 * - publish.remote 가 네트워크 주소(http(s)://, ssh://, git@)
 * - summary.api-key 가 실제 OpenAI 열쇠 모양(sk-…)
 * - nvd.api-key 가 실제 NVD 열쇠 모양(UUID 36자)
 * 시험용 가짜 열쇠("summary-key-…", "nvd-key-…", "test-key")와 임시 저장소 원격("origin")은 통과한다. 값은 오류 문장에 넣지 않는다.
 */
public class TestIsolationGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Pattern NETWORK_REMOTE = Pattern.compile("^(https?://|ssh://|git@).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPENAI_KEY = Pattern.compile("^sk-.+");
    private static final Pattern NVD_KEY = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        check(context.getEnvironment());
    }

    public static void check(ConfigurableEnvironment environment) {
        refuseIf(environment, "publish.remote", NETWORK_REMOTE, "a network git remote");
        refuseIf(environment, "summary.api-key", OPENAI_KEY, "a real-looking summary API key");
        refuseIf(environment, "nvd.api-key", NVD_KEY, "a real-looking NVD API key");
    }

    private static void refuseIf(ConfigurableEnvironment environment, String property, Pattern realLooking,
            String what) {
        String value = environment.getProperty(property);
        if (Objects.nonNull(value) && realLooking.matcher(value.trim()).matches()) {
            throw new IllegalStateException("Test context refused: " + property + " is " + what
                    + ". Tests must not read the real .env or reach real services.");
        }
    }
}
