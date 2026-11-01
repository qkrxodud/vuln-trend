#!/bin/bash
# 하루 한 번 실행(launchd 가 부름). 수집 → 점수 → 통계 → 요약 → 출력 → 게시를 한 번 돈다.
# 비밀 값은 이 파일에 두지 않는다. 배치는 저장소 루트의 .env 를 읽는다.
# 기록: ~/Library/Logs/vuln-trend/daily-YYYYMMDD-HHMM.log (최근 30개만 남김)
# 실패하면 macOS 알림을 띄우고 종료 코드 1 로 끝난다.
set -uo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$HOME/Library/Logs/vuln-trend"
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/daily-$(date +%Y%m%d-%H%M).log"

# launchd 는 PATH 가 거의 비어 있다. 수동 실행 때와 같은 도구를 쓰도록 맞춘다
export PATH="/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin"
export JAVA_HOME="${VULNTREND_JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.8/Contents/Home}"

notify() {
  /usr/bin/osascript -e "display notification \"$1\" with title \"VulnTrend 하루 실행\"" >/dev/null 2>&1 || true
}

# 하위 셸로 묶는다 — 안의 exit 가 스크립트 전체를 끝내면 아래 알림·정리가 안 돈다
(
  echo "== $(date '+%Y-%m-%d %H:%M:%S %Z') start"

  # 승인·커밋되지 않은 코드로 돌지 않는다(공유 작업 폴더라 개발 중인 파일이 섞일 수 있음, 2026-11-01 사고)
  dirty="$(git -C "$REPO" status --porcelain -- batch scripts/run-daily.sh)"
  if [ -n "$dirty" ]; then
    echo "Uncommitted changes under batch/; skipping this run:"
    echo "$dirty"
    exit 2
  fi

  # Docker 가 꺼져 있으면 켜고 최대 3분 기다린다
  if ! docker info >/dev/null 2>&1; then
    echo "Docker is not running; starting Docker Desktop."
    open -a Docker
    for _ in $(seq 1 36); do
      docker info >/dev/null 2>&1 && break
      sleep 5
    done
  fi
  if ! docker info >/dev/null 2>&1; then
    echo "Docker did not start; skipping this run."
    exit 1
  fi

  # DB 컨테이너(재시작 정책 없음)를 띄우고 받아들일 때까지 기다린다
  (cd "$REPO" && docker compose up -d db) || exit 1
  for _ in $(seq 1 30); do
    docker exec vuln-trend-db-1 pg_isready -U vuln -d vuln >/dev/null 2>&1 && break
    sleep 2
  done

  # push 진단(R11): git 명령 흐름만 파일로 남긴다(통신 내용·토큰은 남기지 않음 — GIT_TRACE_CURL 은 쓰지 않음)
  export GIT_TRACE="$LOG_DIR/git-trace-$(date +%Y%m%d-%H%M).log"
  # push 시간 한도를 2분에서 10분으로(launchd 아래에서 2분 초과가 두 번 남)
  cd "$REPO/batch" && ./gradlew --no-daemon -q bootRun --args='--publish.command-timeout=10m'
  status=$?
  echo "== $(date '+%Y-%m-%d %H:%M:%S %Z') end, exit code $status"
  exit $status
) >>"$LOG" 2>&1
status=$?

# 오래된 기록 정리(최근 30개)
ls -1t "$LOG_DIR"/daily-*.log 2>/dev/null | tail -n +31 | while read -r old; do rm -f "$old"; done

if [ $status -eq 2 ]; then
  notify "커밋 안 된 batch 변경이 있어 건너뛰었습니다. 기록: $LOG"
elif [ $status -ne 0 ]; then
  notify "실패했습니다(종료 코드 $status). 기록: $LOG"
else
  notify "완료했습니다."
fi
exit $status
