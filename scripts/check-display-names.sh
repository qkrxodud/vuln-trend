#!/usr/bin/env bash
# 테스트 이름(@DisplayName, 파라미터 테스트 name, Named 케이스 이름)에 개발 용어가 섞였는지 검사한다.
# 기준: .claude/skills/domain-test-writing/SKILL.md §3
# 종료 코드: 0 = 통과, 1 = 금지 용어 발견
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# 검사 대상(스크립트 자체를 시험할 때만 CHECK_TEST_DIR 로 바꾼다)
TEST_DIR="${CHECK_TEST_DIR:-$ROOT/batch/src/test/java}"
[ -d "$TEST_DIR" ] || { echo "✅ 테스트 이름 — 검사 대상 없음($TEST_DIR 없음)"; exit 0; }

# 대소문자 무시. 한국어 용어는 부분 일치로 검사한다
# macOS 기본 grep(BSD)은 단어 경계 \b 를 지원하지 않으므로, 짧은 영어 용어는 앞뒤가 영문자가 아닌지로 경계를 표현한다
WORDS='DB|ID|row|PK|JPA|SQL|true|false|DTO'
BANNED="예외|exception|throw|null|빈 문자열|테이블|컬럼|UUID|유일 제약|unique|인덱스|엔티티|entity|객체|인스턴스|마이그레이션|flyway|매핑|스키마|코드 ?포인트|서러게이트|인코딩|반환|리턴|return|트랜잭션|롤백|타임스탬프|timestamp|upsert|배치 ?러너|JdbcTemplate|JSONB|jsonb|save\\(|find\\(|(^|[^A-Za-z])(${WORDS})([^A-Za-z]|$)"

# 검사 대상: @DisplayName("..."), @ParameterizedTest(name = "..."), named("...", ...)
# 이름 문자열만 뽑아서 검사한다(named("이름", 값) 의 값 쪽은 검사하지 않음)
# 파일 경로(클래스명)는 검사하지 않는다 — "경로:줄:" 뒤의 이름 문자열에만 금지 용어를 대조한다
matches=""
while IFS= read -r line; do
  [ -z "$line" ] && continue
  name="${line#*:}"   # 경로 제거
  name="${name#*:}"   # 줄 번호 제거
  if printf '%s\n' "$name" | grep -qiE "$BANNED"; then
    matches="${matches}${line}"$'\n'
  fi
done < <(grep -rnoE '@DisplayName\("[^"]*"|@ParameterizedTest\(name *= *"[^"]*"|named\("[^"]*"' "$TEST_DIR")

if [ -n "$matches" ]; then
  echo "❌ 테스트 이름에 개발 용어가 있습니다 (도메인 언어로 바꿔 주세요):"
  printf '%s' "$matches" | sed "s|$ROOT/||; s/^/   /"
  exit 1
fi

count="$(grep -rcE '@DisplayName\("' "$TEST_DIR" | awk -F: '{s+=$2} END {print s+0}')"
echo "✅ 테스트 이름 ${count}개 — 개발 용어 없음"
