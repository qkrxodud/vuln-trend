package com.vulntrend.batch.domain.score;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 트렌드 점수의 항별 기여도(score_breakdown, 기획서 6장 MVP 점수식, G1 계약).
 * kev·ransomware·epss·cvss 는 감쇠 전 기여, news·poc 는 2단계 항이라 0, decay 는 시간 감쇠로 빠진 점수(0 이하)다.
 * 일곱 항을 더하면 점수가 된다. 사이트에는 항을 소수 첫째 자리로, 점수를 그 항들의 합을 정수로 반올림해 보인다(R6) —
 * 보이는 항을 더하면 보이는 점수가 되도록 하기 위해서다(프론트 목업 계산과 같다).
 */
@Getter
public class ScoreBreakdown {

    private static final int TERM_SCALE = 1;

    private final BigDecimal kev;
    private final BigDecimal ransomware;
    private final BigDecimal epss;
    private final BigDecimal news;
    private final BigDecimal poc;
    private final BigDecimal cvss;
    private final BigDecimal decay;

    @Builder(access = AccessLevel.PRIVATE)
    private ScoreBreakdown(BigDecimal kev, BigDecimal ransomware, BigDecimal epss, BigDecimal news, BigDecimal poc,
            BigDecimal cvss, BigDecimal decay) {
        this.kev = kev;
        this.ransomware = ransomware;
        this.epss = epss;
        this.news = news;
        this.poc = poc;
        this.cvss = cvss;
        this.decay = decay;
    }

    /** 감쇠 비율은 0.5^(경과일/30). 아주 오래된 CVE 는 저장소 계산에서 0 이 될 수 있다 */
    public static ScoreBreakdown of(BigDecimal kev, BigDecimal ransomware, BigDecimal epss, BigDecimal cvss,
            BigDecimal decayFactor) {
        Stream.of(kev, ransomware, epss, cvss).forEach(ScoreBreakdown::requireTerm);
        requireFactor(decayFactor);
        BigDecimal raw = kev.add(ransomware).add(epss).add(cvss);
        return ScoreBreakdown.builder()
                .kev(kev)
                .ransomware(ransomware)
                .epss(epss)
                .news(BigDecimal.ZERO)
                .poc(BigDecimal.ZERO)
                .cvss(cvss)
                .decay(raw.multiply(decayFactor).subtract(raw))
                .build();
    }

    public BigDecimal total() {
        return Stream.of(kev, ransomware, epss, news, poc, cvss, decay).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 사이트에 보일 항: 소수 첫째 자리 */
    public ScoreBreakdown rounded() {
        return ScoreBreakdown.builder()
                .kev(oneDecimal(kev))
                .ransomware(oneDecimal(ransomware))
                .epss(oneDecimal(epss))
                .news(oneDecimal(news))
                .poc(oneDecimal(poc))
                .cvss(oneDecimal(cvss))
                .decay(oneDecimal(decay))
                .build();
    }

    /** 사이트에 보일 점수: 보이는 항의 합을 정수로 */
    public BigDecimal score() {
        return rounded().total().setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal oneDecimal(BigDecimal value) {
        return value.setScale(TERM_SCALE, RoundingMode.HALF_UP);
    }

    private static void requireTerm(BigDecimal term) {
        if (Objects.isNull(term) || term.signum() < 0) {
            throw new DomainValidationException("A score term must be zero or more.");
        }
    }

    private static void requireFactor(BigDecimal factor) {
        if (Objects.isNull(factor) || factor.signum() < 0 || factor.compareTo(BigDecimal.ONE) > 0) {
            throw new DomainValidationException("A decay factor must be between 0 and 1.");
        }
    }
}
