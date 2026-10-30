#!/usr/bin/env bash
# record 는 DTO 에서만 쓴다(사용자 규칙). dto 패키지 밖의 record 선언을 찾아 실패시킨다.
# 대상: batch/src/main, batch/src/test 전부. 엔티티·값 객체·서비스 내부 값·테스트 도우미는 일반 클래스로 쓴다.
# 종료 코드: 0 = 통과, 1 = dto 밖 record 발견
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${CHECK_SRC_DIR:-$ROOT/batch/src}"
[ -d "$SRC" ] || { echo "✅ record — 검사 대상 없음($SRC 없음)"; exit 0; }

# 선언 모양: (줄 시작·공백·{·; 뒤) [접근제어자] [static] record 이름( 또는 <
# 문자열 속("record X(")은 앞이 따옴표라 걸리지 않고, // · * 로 시작하는 주석 줄은 제외한다
DECL='(^|[[:space:]{;])(public |private |protected )?(static )?(final )?record [A-Z][A-Za-z0-9_]*[[:space:]]*[(<]'
found="$(grep -rnE "$DECL" "$SRC" --include='*.java' \
  | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|\*)' \
  | grep -v '/dto/' || true)"

if [ -n "$found" ]; then
  echo "❌ dto 패키지 밖에 record 가 있습니다 (일반 클래스로 바꾸거나 DTO 라면 dto 패키지로 옮기세요):"
  printf '%s\n' "$found" | sed "s|$ROOT/||; s/^/   /"
  exit 1
fi

count="$(grep -rnE "$DECL" "$SRC" --include='*.java' | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|\*)' | wc -l | tr -d ' ')"
echo "✅ record ${count}개 — 모두 dto 패키지 안"
