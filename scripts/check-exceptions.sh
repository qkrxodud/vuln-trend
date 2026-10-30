#!/usr/bin/env bash
# 예외는 한 곳(global/error)에서 관리한다(사용자 규칙).
#  1) 예외 클래스 선언은 global/error/exception 에만 둔다(공통 조상 VulnTrendException 은 global/error).
#  2) global 은 domain·application·infrastructure·runner 를 참조하지 않는다(모든 계층이 쓰는 공통 영역 — 순환 방지).
# 종료 코드: 0 = 통과, 1 = 위반
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="${CHECK_MAIN_DIR:-$ROOT/batch/src/main/java}"
PACKAGE_ROOT="${CHECK_PACKAGE_ROOT:-com.vulntrend.batch}"   # 결정 대기 — check-conventions.py 와 같은 값
PACKAGE_DIR="$(printf '%s' "$PACKAGE_ROOT" | tr . /)"
[ -d "$MAIN" ] || { echo "✅ 예외 — 검사 대상 없음($MAIN 없음)"; exit 0; }
status=0

misplaced="$(grep -rnE '(^|[[:space:]])class [A-Z][A-Za-z0-9_]*Exception[[:space:]]+extends' "$MAIN" --include='*.java' \
  | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|\*)' \
  | grep -v '/global/error/exception/' \
  | grep -v '/global/error/VulnTrendException.java' || true)"
if [ -n "$misplaced" ]; then
  echo "❌ global/error/exception 밖에 예외 클래스가 있습니다:"
  printf '%s\n' "$misplaced" | sed "s|$ROOT/||; s/^/   /"
  status=1
fi

leaking="$(grep -rnE "^import ${PACKAGE_ROOT//./\\.}\.(domain|application|infrastructure|runner)\." "$MAIN/$PACKAGE_DIR/global" 2>/dev/null || true)"
if [ -n "$leaking" ]; then
  echo "❌ global 이 다른 계층을 참조합니다(순환 위험):"
  printf '%s\n' "$leaking" | sed "s|$ROOT/||; s/^/   /"
  status=1
fi

if [ $status -eq 0 ]; then
  count="$(grep -rlE 'class [A-Z][A-Za-z0-9_]*Exception[[:space:]]+extends' "$MAIN" --include='*.java' | wc -l | tr -d ' ')"
  echo "✅ 예외 ${count}개 — 모두 global/error 안, global 은 다른 계층을 참조하지 않음"
fi
exit $status
