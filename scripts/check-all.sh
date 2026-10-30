#!/usr/bin/env bash
# 완료 전 전체 검사 — 하나라도 실패하면 작업 단위를 완료로 보고하지 않는다.
#   0) 코드 규칙 검사 도구의 회귀 시험   1) record 위치   2) 예외 위치·global 독립
#   3) 코드 규칙(생성자·if(!)·빈 상태·setter·계층 방향·@Value·람다 !·import 순서·설정 자리표시자·Fake 위치·예외 문장 로그)
#   4) 테스트 이름(도메인 언어)   5) 빌드·테스트·커버리지(batch/ 에서 ./gradlew clean check)
# 사용: bash scripts/check-all.sh [--skip-gradle]
#   --skip-gradle : 빠른 반복 중 규칙만 볼 때. 완료 판정에는 쓰지 않는다.
# 판정은 출력 문구가 아니라 각 검사의 종료 코드로 한다.
# (clarity-api 하네스의 check-all.sh 에서 가져옴. DBML·ErrorCode 표 대조는 그 프로젝트 전용이라 빼고, Gradle 루트를 batch/ 로 바꿨다.)
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
SKIP_GRADLE=0
for arg in "$@"; do
  case "$arg" in
    --skip-gradle) SKIP_GRADLE=1 ;;
  esac
done

results=()
failed=0
run() {
  local label="$1"; shift
  local log
  log="$(mktemp)"
  if "$@" > "$log" 2>&1; then
    results+=("✅ $label")
  else
    results+=("❌ $label")
    failed=1
    echo "── $label 실패 ──"
    tail -n 25 "$log"
  fi
  rm -f "$log"
}

run "코드 규칙 검사 도구 회귀 시험"     python3 scripts/tests/run-convention-tests.py
run "record 는 dto 안에만"            bash scripts/check-records.sh
run "예외는 global/error, global 독립" bash scripts/check-exceptions.sh
run "코드 규칙"                       python3 scripts/check-conventions.py
run "테스트 이름(도메인 언어)"         bash scripts/check-display-names.sh
if [ $SKIP_GRADLE -eq 1 ]; then
  results+=("⏭  빌드·테스트·커버리지 (건너뜀 — 완료 판정 불가)")
elif [ -x batch/gradlew ]; then
  run "빌드·테스트·커버리지 90%"       bash -c 'cd batch && ./gradlew clean check'
else
  results+=("⏭  빌드·테스트·커버리지 (batch/gradlew 없음 — Phase 0.3 골격 전)")
  SKIP_GRADLE=1
fi

echo
echo "== 전체 검사 결과 =="
printf '%s\n' "${results[@]}"
if [ $failed -ne 0 ]; then
  echo "❌ 실패한 검사가 있습니다. 작업 단위를 완료로 보고하지 마세요."
  exit 1
fi
if [ $SKIP_GRADLE -eq 1 ]; then
  echo "⚠️  건너뛴 검사가 있어 완료 판정에는 쓸 수 없습니다."
  exit 0
fi
echo "✅ 모든 검사 통과"
