package com.vulntrend.batch.application.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.fake.FakeAttackTypeAssignmentRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisplayName("공격 유형 매기기")
@ExtendWith(OutputCaptureExtension.class)
class AttackTypeAssignServiceTest {

    private static final CveId XSS_BUG = CveId.from("CVE-2026-0001");
    private static final CveId SQL_BUG = CveId.from("CVE-2026-0002");
    private static final CveId NO_INFO = CveId.from("CVE-2026-0003");

    private final FakeAttackTypeAssignmentRepository repository = new FakeAttackTypeAssignmentRepository();

    @Test
    @DisplayName("저장된 약점 번호로 공격 유형을 매겨 유형 키를 정해진 순서로 남긴다")
    void assignsFromWeaknesses(CapturedOutput output) {
        repository.with(XSS_BUG, List.of("CWE-79", "CWE-89"), List.of());

        int changed = new AttackTypeAssignService(repository).assignAll();

        assertThat(changed).isEqualTo(1);
        assertThat(repository.keysOf(XSS_BUG)).containsExactly("injection", "xss");
        assertThat(output).contains("Attack types changed for 1 CVEs");
    }

    @Test
    @DisplayName("이미 같은 유형이 남아 있는 CVE 는 다시 쓰지 않아 두 번 돌려도 바뀌는 것이 없다")
    void writesOnlyChanges() {
        repository.with(XSS_BUG, List.of("CWE-79"), List.of("xss"))
                .with(SQL_BUG, List.of("CWE-89"), List.of());
        AttackTypeAssignService service = new AttackTypeAssignService(repository);

        assertThat(service.assignAll()).isEqualTo(1);
        assertThat(repository.lastChanges()).containsOnlyKeys(SQL_BUG);

        assertThat(service.assignAll()).isZero();
    }

    @Test
    @DisplayName("약점이 정보 없음뿐이거나 사라진 CVE 는 분류 전(빈 목록)으로 되돌린다")
    void resetsWhenNoUsableWeakness() {
        repository.with(NO_INFO, List.of("NVD-CWE-noinfo"), List.of("xss"))
                .with(XSS_BUG, List.of(), List.of("other"));

        new AttackTypeAssignService(repository).assignAll();

        assertThat(repository.keysOf(NO_INFO)).isEmpty();
        assertThat(repository.keysOf(XSS_BUG)).isEmpty();
    }

    @Test
    @DisplayName("분류 전인 CVE 가 계속 분류 전이면 쓰지 않는다")
    void unclassifiedStaysUntouched() {
        repository.with(NO_INFO, List.of("NVD-CWE-Other"), List.of());

        assertThat(new AttackTypeAssignService(repository).assignAll()).isZero();
    }
}
