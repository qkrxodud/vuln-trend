#!/usr/bin/env python3
"""
check-conventions.py 고정 회귀 시험.

conventions/ 아래 시험 파일(잡을 것·잡지 말 것)에 검사 스크립트를 돌려, 나온 위반을 expected.txt 와 비교한다.
기대에 없는 위반(오탐)과 기대에 있는데 안 나온 위반(놓침)을 모두 실패로 본다.
시험 파일은 src 밖(scripts/tests)에 있어 Gradle 빌드·다른 검사 스크립트(src 만 봄)에 들어가지 않는다.

사용: python3 scripts/tests/run-convention-tests.py      종료 코드: 0 = 기대와 같음, 1 = 다름
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
FIXTURES = os.path.join(HERE, "conventions")
SCRIPT = os.path.join(ROOT, "scripts", "check-conventions.py")

# 검사 메시지 앞부분 → 규칙 이름. 모르는 메시지가 나오면 실패한다(새 규칙이면 여기와 expected.txt 에 추가)
RULES = [
    ("생성자를 직접 썼습니다", "ctor"),
    ("if (!…)", "if-not"),
    ("싱글톤 빈의 필드는 final", "bean-field"),
    ("싱글톤 빈에 바뀌는 그릇", "bean-mutable"),
    ("setter 대신", "setter"),
    ("계층 방향 위반", "layer"),
    ("@Value 대신", "value"),
    ("람다 안의 !", "lambda-not"),
    ("import 순서", "import-order"),
    ("기본값 없는 자리표시자", "placeholder"),
    ("가짜 구현체(Fake*)", "fake-location"),
    ("타입 선언을 알아보지 못했습니다", "no-type"),
    ("프레임워크 예외의 문장을 로그에", "log-message"),
]
LINE = re.compile(r"^\s+(\S+):(\d+): (.*?)(?: — (.*))?$")
# 빠진 이름 목록까지 대조하는 규칙 — 기대는 rule[A, B] 꼴(순서·공백 무관). 지금은 없음(규칙 12 가 웹 전용이라 빠짐)
DETAIL_RULES = set()


def normalize(key):
    """`rule[A,B]` 꼴의 이름 목록을 정렬·공백 정리해 순서·공백 차이로 틀리지 않게 한다(검사자 r3 신호 S2)"""
    if "[" not in key:
        return key
    head, detail = key.split("[", 1)
    names = sorted(name.strip() for name in detail.rstrip("]").split(",") if name.strip())
    return f"{head}[{', '.join(names)}]"


def rule_of(message):
    for prefix, rule in RULES:
        if message.startswith(prefix):
            return rule
    return None


def expected():
    found = set()
    for line in open(os.path.join(FIXTURES, "expected.txt"), encoding="utf-8"):
        line = line.split("#", 1)[0].strip()
        if line:
            location, rule = line.split(maxsplit=1)
            found.add(f"{location} {normalize(rule)}")
    return found


def actual():
    run = subprocess.run(
        [sys.executable, SCRIPT,
         os.path.join(FIXTURES, "main", "java"),
         os.path.join(FIXTURES, "test", "java"),
         os.path.join(FIXTURES, "resources")],
        capture_output=True, text=True)
    found, unknown = set(), []
    for line in run.stdout.splitlines():
        matched = LINE.match(line)
        if not matched:
            continue
        path = os.path.relpath(os.path.join(ROOT, matched.group(1)), FIXTURES).replace(os.sep, "/")
        rule = rule_of(matched.group(3))
        if rule is None:
            unknown.append(line.strip())
            continue
        if rule in DETAIL_RULES:
            rule = normalize(f"{rule}[{matched.group(4) or ''}]")
        found.add(f"{path}:{matched.group(2)} {rule}")
    return found, unknown, run.returncode, run.stderr


def main():
    want = expected()
    got, unknown, code, stderr = actual()
    missed = sorted(want - got)
    extra = sorted(got - want)
    if stderr.strip():
        print(stderr.strip())
    for item in missed:
        print(f"   놓침: {item}")
    for item in extra:
        print(f"   오탐: {item}")
    for item in unknown:
        print(f"   모르는 메시지: {item}")
    # 위반이 있는 시험 세트이므로 검사 스크립트는 1 로 끝나야 한다(0 이면 판정 자체가 고장)
    if missed or extra or unknown or code != 1:
        print(f"❌ 코드 규칙 검사 회귀 시험 실패 — 놓침 {len(missed)}, 오탐 {len(extra)}, 모르는 메시지 {len(unknown)}, 검사 종료 코드 {code}")
        sys.exit(1)
    print(f"✅ 코드 규칙 검사 회귀 시험 — 기대 위반 {len(want)}건과 정확히 일치")


if __name__ == "__main__":
    main()
