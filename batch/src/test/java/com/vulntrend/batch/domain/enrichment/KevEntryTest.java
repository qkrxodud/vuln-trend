package com.vulntrend.batch.domain.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("실제 악용 목록(KEV)의 한 건")
class KevEntryTest {

    private static final CveId LOG4SHELL = CveId.from("CVE-2021-44228");
    private static final LocalDate ADDED = LocalDate.of(2021, 12, 10);
    private static final LocalDate DUE = LocalDate.of(2021, 12, 24);
    private static final String PAYLOAD = "{\"cveID\":\"CVE-2021-44228\"}";

    @Nested
    @DisplayName("목록에서 읽을 때")
    class Reading {

        @Test
        @DisplayName("CVE 번호와 등재일·조치 기한과 원본을 그대로 담는다")
        void keepsFields() {
            KevEntry entry = KevEntry.of(LOG4SHELL, ADDED, DUE, "Known", PAYLOAD);

            assertThat(entry.getCveId()).isEqualTo(LOG4SHELL);
            assertThat(entry.getDateAdded()).isEqualTo(ADDED);
            assertThat(entry.getDueDate()).contains(DUE);
            assertThat(entry.getPayload()).isEqualTo(PAYLOAD);
        }

        @ParameterizedTest(name = "\"{0}\" 이면 랜섬웨어에 쓰임: {1}")
        @CsvSource({"Known, yes", "known, yes", "' Known ', yes", "Unknown, no", "'', no"})
        @DisplayName("CISA 가 랜섬웨어 캠페인에 쓰였다고 밝힌 것만 랜섬웨어에 쓰인 것으로 본다")
        void ransomwareOnlyWhenKnown(String use, String expected) {
            KevEntry entry = KevEntry.of(LOG4SHELL, ADDED, DUE, use, PAYLOAD);

            assertThat(entry.isRansomware()).isEqualTo("yes".equals(expected));
        }

        @Test
        @DisplayName("랜섬웨어 표시가 아예 없으면 랜섬웨어에 쓰이지 않은 것으로 본다")
        void ransomwareMissingIsNo() {
            assertThat(KevEntry.of(LOG4SHELL, ADDED, DUE, null, PAYLOAD).isRansomware()).isFalse();
        }

        @Test
        @DisplayName("조치 기한이 없으면 기한 없이 담는다")
        void dueDateMayBeAbsent() {
            assertThat(KevEntry.of(LOG4SHELL, ADDED, null, "Known", PAYLOAD).getDueDate()).isEmpty();
        }
    }

    @Nested
    @DisplayName("받을 수 없는 건")
    class Refusing {

        @Test
        @DisplayName("CVE 번호가 없으면 받지 않는다")
        void missingIdIsRefused() {
            assertThatThrownBy(() -> KevEntry.of(null, ADDED, DUE, "Known", PAYLOAD))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("등재일이 없으면 점수의 감쇠 기준을 정할 수 없어 받지 않는다")
        void missingDateAddedIsRefused() {
            assertThatThrownBy(() -> KevEntry.of(LOG4SHELL, null, DUE, "Known", PAYLOAD))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("원본이 비어 있으면 받지 않는다")
        void blankPayloadIsRefused() {
            assertThatThrownBy(() -> KevEntry.of(LOG4SHELL, ADDED, DUE, "Known", " "))
                    .isInstanceOf(DomainValidationException.class);
        }
    }
}
