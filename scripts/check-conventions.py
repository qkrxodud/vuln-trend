#!/usr/bin/env python3
"""
vuln-trend 배치(batch/) 코드 규칙 검사. 사용자 규칙과 Spring Boot 개발 가이드 중 기계로 잡을 수 있는 것을 강제한다.
(clarity-api 하네스의 check-conventions.py 를 가져와 패키지·계층 이름만 바꿨다. 규칙 번호는 그대로 둔다.)

  1. 생성자 직접 작성 금지
     - Spring 빈은 @RequiredArgsConstructor(+ private final 필드)로 주입한다.
       설정 값은 @ConfigurationProperties 클래스로, 시작 시 검사는 @PostConstruct 로 한다.
     - 빈이 아닌 객체는 private @Builder 생성자 + 정적 팩터리(of/from)로 만든다.
     - 허용: @Builder 가 붙은 private 생성자, 예외 클래스(global/error), JPA 용 protected 기본 생성자는 Lombok(@NoArgsConstructor)로.
     - 빈이 아니고 밖에 노출되지 않는 private 중첩 값 클래스는 Lombok @RequiredArgsConstructor(access = PRIVATE) + 정적 팩터리도 된다(사용자 결정 2026-10-28).
     - enum 도 이 규칙을 따른다 — 손 생성자 대신 Lombok @RequiredArgsConstructor(사용자 결정 2026-10-28). Lombok 생성자는 손 생성자가 아니므로 검사에 걸리지 않는다.
     - 검사 범위: 최상위·중첩·깊은 중첩·지역 class 와 enum 의 생성자. 주석·문자열을 비운 코드를 토큰 단위로 훑으며 괄호 깊이를 따라가
       타입 본문 바로 아래에서 "[애너테이션] [접근자] [<타입 매개변수>] 타입이름(" 을 찾는다 — 같은 줄 애너테이션, 한 줄 선언, 제네릭 생성자도 본다.
       메서드 안의 호출·new·익명 클래스·람다는 보지 않는다. global/error 의 면제는 …Exception 을 상속한 타입 자신에게만(그 안 중첩 클래스는 검사).
       record 는 check-records.sh 가 본다. 못 보는 것: 유니코드 이스케이프(\u0022)로 쓴 따옴표, 타입 머리(extends 절 등)의 애너테이션 인자 속 { }.
     - 이 스크립트의 회귀 시험: scripts/tests/run-convention-tests.py (check-all.sh 첫 단계).
  2. if (!...) 금지 — "아니다"를 뜻하는 함수(isNotX, Objects.isNull 등)로 쓴다.
  3. 싱글톤 빈의 상태 금지 — @Component/@Service/@Repository/@Controller/@RestController/@Configuration 클래스의
     인스턴스 필드는 모두 final 이어야 한다(여러 요청이 동시에 쓰는 값이 바뀌면 경쟁 조건).
     3-b. final 필드라도 바뀌는 그릇(new ConcurrentHashMap/ArrayList/Atomic… )을 담으면 위반. 허용 목록(ALLOWED_STATEFUL_BEANS)만 예외.
  4. setter 금지 — 값 변경은 change*/행위 이름 메서드로. 외부 API 가 강제하는 @Override 재정의는 제외.
  (5~9 는 아래 주석) 10. 가짜 구현체(Fake*)는 test/fake 에만.
  11. 프레임워크·외부 예외의 문장을 로그에 남기지 않는다 — log.*(…, e.getMessage()) 는 e 의 선언 타입이 우리 예외(global/error 의
      VulnTrendException 계열)일 때만 허용. HTTP 클라이언트·Jackson 의 문장에는 외부 API 응답 본문·URL(API 키가 든 쿼리)이 그대로 들어 있다.
      예외 객체 자체를 넘기는 log.error("…", e) 는 스택을 남기는 것이라 보지 않는다(error 로그는 받은 값과 무관한 500 경로).
  (12 는 웹 전역 예외 처리기 규칙 — 배치에는 웹이 없어 가져오지 않았다. 웹 모듈이 생기면 clarity-api 의 check_handlers 를 되살린다.)

대상: batch/src/main/java 전체 + batch/src/test/java 일부 규칙(if (!, @Value, 람다 !, 계층 방향, import 순서, Fake 위치) + batch/src/main/resources 의 application*.yml|properties.

사용: python3 scripts/check-conventions.py [main/java 경로] [test/java 경로] [main/resources 경로]
패키지 루트가 바뀌면 PACKAGE_ROOT 하나만 고친다(결정 대기: 기본 com.vulntrend.batch).
종료 코드: 0 = 통과, 1 = 위반
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BATCH = os.path.join(ROOT, "batch")
MAIN = sys.argv[1] if len(sys.argv) > 1 else os.path.join(BATCH, "src", "main", "java")
TEST = sys.argv[2] if len(sys.argv) > 2 else os.path.join(BATCH, "src", "test", "java")
RESOURCES = sys.argv[3] if len(sys.argv) > 3 else os.path.join(BATCH, "src", "main", "resources")

# 패키지 루트(결정 대기 — Phase 0.3 Gradle 골격을 만들 때 확정). 디렉터리·import 검사가 모두 이 값을 쓴다
PACKAGE_ROOT = os.environ.get("CHECK_PACKAGE_ROOT", "com.vulntrend.batch")
PACKAGE_DIR = "/" + PACKAGE_ROOT.replace(".", "/") + "/"
BASE_EXCEPTION = "VulnTrendException"

STEREOTYPE = re.compile(r"^@(Component|Service|Repository|Controller|RestController|Configuration|RestControllerAdvice)\b", re.M)
# 최상위 타입 선언. 같은 줄 애너테이션(@A, @a.B, @A(…) — 괄호 한 겹까지)과 sealed/non-sealed/strictfp 등 수식어를 허용한다.
# 알아보지 못하면 check_file 이 그 파일을 위반으로 알린다(예전엔 파일의 모든 규칙을 조용히 건너뜀 — 단위 11 재검사 지적)
TYPE_DECL = re.compile(
    r"^[ \t]*(?:@[\w.]+(?:\([^()]*\))?\s+)*"
    r"(?:(?:public|protected|private|abstract|final|sealed|non-sealed|static|strictfp)\s+)*"
    r"(class|enum|interface|record|@interface)\s+(\w+)", re.M)


# 코드만 남기기: 주석과 문자열·텍스트 블록·문자 리터럴을 한 번에 왼쪽부터 훑어 비운다.
# 주석과 리터럴을 따로 처리하면 문자열 속 "/api/**" 의 /* 나 "http://x" 의 // 가 주석으로 읽혀 코드를 삼킨다(단위 11 검사자 지적).
# 비운 자리는 공백으로 채우고 줄바꿈은 남겨서 줄 번호와 열 위치가 원문과 같다. 따옴표는 남긴다("…" → "   ").
# 모든 줄 단위 규칙과 생성자 규칙이 이 결과를 쓴다(import 순서만 원문을 본다)
def code_only(source):
    out = []
    i, n = 0, len(source)

    def blank(text):
        return "".join("\n" if c == "\n" else " " for c in text)

    while i < n:
        c = source[i]
        nxt = source[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            end = source.find("\n", i)
            end = n if end < 0 else end
            out.append(blank(source[i:end]))
            i = end
        elif c == "/" and nxt == "*":
            end = source.find("*/", i + 2)
            end = n if end < 0 else end + 2
            out.append(blank(source[i:end]))
            i = end
        elif source.startswith('"""', i):
            j = i + 3
            while j < n and not source.startswith('"""', j):
                j += 2 if source[j] == "\\" else 1
            end = min(n, j + 3)
            out.append('"""' + blank(source[i + 3:j]) + source[j:end])
            i = end
        elif c in "\"'":
            j = i + 1
            while j < n and source[j] != c and source[j] != "\n":
                j += 2 if source[j] == "\\" else 1
            j = min(j, n)
            closing = source[j] if j < n and source[j] == c else ""
            out.append(c + blank(source[i + 1:j]) + closing)
            i = j + len(closing)
        else:
            out.append(c)
            i += 1
    return "".join(out)


# 싱글톤 빈 상태 규칙의 허용 예외(vuln-trend-dev-conventions §3). 비어 있다 — 늘리려면 사용자 결정이 필요하다
ALLOWED_STATEFUL_BEANS = set()
LOG_CALL = re.compile(r"\b(?:log|logger|LOGGER|LOG)\.(?:trace|debug|info|warn|error|at(?:Trace|Debug|Info|Warn|Error)\(\)(?:\.\w+\([^()]*\))*\.log)\s*\(")
MESSAGE_CALL = re.compile(r"(\w+)((?:\.\w+\([^()]*\))*)\.(get(?:Localized)?Message|toString)\(\)")
CALL_RESULT_MESSAGE = re.compile(r"\)\s*\.get(?:Localized)?Message\(\)")
THROWABLE_NAME = re.compile(r"(Exception|Error|Throwable)$")
MUTABLE_FIELD = re.compile(
    r"    (private |protected |public )?final [^=(]+=\s*new (Concurrent\w*|Hash\w*|Linked\w*|Tree\w*|ArrayList|ArrayDeque|CopyOnWrite\w*|Atomic\w*|WeakHashMap|IdentityHashMap|EnumMap)\b")

# 배치에는 웹(presentation)이 없다. 진입점(CommandLineRunner·CLI 인자 해석)은 runner 가 맡는다
LAYER_IMPORTS = {
    "domain": {"domain", "global"},
    "application": {"application", "domain", "global"},
    "runner": {"runner", "application", "domain", "global"},
    "infrastructure": {"infrastructure", "domain", "global"},
    "global": {"global"},
    # 가짜(test/fake)는 도메인 창구와 애플리케이션 창구를 흉내 낸다. 러너·인프라는 보지 않는다
    "fake": {"fake", "application", "domain", "global"},
}
# 테스트는 같은 계층 규칙에 더해 fake(가짜 저장소)와 support(Testcontainers 등 테스트 공용 설정)를 쓸 수 있다.
# support 는 src/test 에만 있고 어느 계층이든 import 할 수 있다(백엔드 A 보고 2026-10-30 — 공용 설정이 계층 검사에 걸림)
TEST_EXTRA = {"fake", "support"}


def our_exceptions(main_root):
    """global/error 아래 모든 .java 의 이름 — 우리 예외는 문장이 고정(받은 값 없음)이라 로그에 남겨도 된다"""
    names = {BASE_EXCEPTION}
    error_dir = os.path.join(main_root, *PACKAGE_ROOT.split("."), "global", "error")
    for directory, _, files in os.walk(error_dir):
        names.update(f[:-5] for f in files if f.endswith(".java"))
    return names


def nearest_declared_type(code, variable, before):
    """로그 줄(before: 문자 위치) 앞에서 가장 가까운 변수 선언의 타입(매개변수·catch·다중 catch·지역 변수).
    자바는 선언이 사용보다 앞서고 매개변수를 지역 변수로 가릴 수 없으므로 "가장 가까운 앞 선언"이 범위 판단의 근사다
    (첫 선언만 보면 같은 이름이 다른 메서드에서 다른 타입으로 쓰일 때 놓침·오탐 — 검사자·리뷰어 지적 2026-10-30).
    필드(`private final Exception failure;`)는 선언 모양이 달라 못 찾고 → None → 규칙 11 이 위반으로 알린다(닫힌 쪽, 픽스처 LogProbe.fieldException).
    못 찾으면 None"""
    pattern = r"(?:\(|,|\||catch\s*\(|^\s*)\s*(?:final\s+)?([A-Z]\w*(?:<[^>]*>)?)\s+" + re.escape(variable) + r"\b"
    kind = None
    for found in re.finditer(pattern, code, re.M):
        if found.start() >= before:
            break
        kind = re.sub(r"<.*", "", found.group(1))
    return kind


def log_statements(lines):
    """로그 호출 문장을 (시작 줄 번호, 문장 글) 로 — 여러 줄에 걸친 호출은 ';' 까지 이어 붙인다"""
    i = 0
    while i < len(lines):
        if LOG_CALL.search(lines[i]):
            start, parts = i, []
            while i < len(lines):
                parts.append(lines[i])
                if ";" in lines[i]:
                    break
                i += 1
            yield start, i, " ".join(part.strip() for part in parts)
        i += 1


def message_leaks(statement, code, position, raw_statement, safe_exceptions):
    """규칙 11 — 문장 안에서 프레임워크 예외의 문장이 로그로 나가는 자리를 찾는다. 모르는 것은 통과시키지 않는다"""
    if CALL_RESULT_MESSAGE.search(statement):
        return True
    for variable, chain, method in MESSAGE_CALL.findall(statement):
        kind = nearest_declared_type(code, variable, position)
        if method == "toString" and (kind is None or not THROWABLE_NAME.search(kind)):
            continue  # 예외가 아닌 객체의 toString(StringBuilder 등)은 이 규칙의 대상이 아님
        if chain or kind is None or kind not in safe_exceptions:
            return True
    # 자리표시자 {} 에 예외 객체를 그대로 넣으면 toString(= 종류 + 문장)이 찍힌다. 마지막 인자가 예외이고 {} 가 모자라면 스택 기록(허용)
    arguments = [part.strip() for part in re.split(r",(?![^()]*\))", statement[statement.index("(") + 1:].rsplit(")", 1)[0])][1:]
    placeholders = raw_statement.count("{}")
    for index, argument in enumerate(arguments):
        if not re.fullmatch(r"\w+", argument):
            continue
        kind = nearest_declared_type(code, argument, position)
        if kind is None or not THROWABLE_NAME.search(kind) or kind in safe_exceptions:
            continue
        if index == len(arguments) - 1 and placeholders < len(arguments):
            continue
        return True
    return False


def layer_of(path):
    parts = path.replace(os.sep, "/").split(PACKAGE_DIR)
    if len(parts) < 2:
        return None
    return parts[1].split("/")[0]


def in_error_package(path):
    return "/global/error/" in path.replace(os.sep, "/")


def is_override(lines, index):
    # 바로 위의 비어 있지 않은 줄들(애너테이션 묶음)에 @Override 가 있으면 재정의
    j = index - 1
    while j >= 0 and (lines[j].strip() == "" or lines[j].strip().startswith("@")):
        if lines[j].strip() == "@Override":
            return True
        j -= 1
    return False


TOKEN = re.compile(r"[A-Za-z_$][\w$]*|\S")
TYPE_KEYWORDS = {"class", "enum", "interface", "record"}
ACCESS = {"public", "protected", "private"}


def tokens_with_lines(code):
    line = 0
    last = 0
    for match in TOKEN.finditer(code):
        line += code.count("\n", last, match.start())
        last = match.start()
        yield match.group(0), line


# 애너테이션(@A, @a.b.C, @A(…))이 끝나는 자리 j 에서 (시작 '@' 의 위치, 마지막 이름)을 찾는다. 애너테이션이 아니면 None
def annotation_start(tokens, j):
    if tokens[j][0] == ")":
        level = 0
        while j >= 0:
            if tokens[j][0] == ")":
                level += 1
            elif tokens[j][0] == "(":
                level -= 1
                if level == 0:
                    break
            j -= 1
        j -= 1
    name = tokens[j][0] if j >= 0 else ""
    while j >= 2 and tokens[j - 1][0] == "." and re.match(r"\w", tokens[j - 2][0]):
        j -= 2
    if j >= 1 and re.match(r"[A-Za-z_$]", tokens[j][0]) and tokens[j - 1][0] == "@":
        return j - 1, name
    return None


# 생성자 이름 앞을 거슬러 올라가 접근자·애너테이션·타입 매개변수(<U>)만 있고 그 앞이 { } ; 인지 본다.
# 맞으면 (private 여부, 붙은 애너테이션 이름들), 아니면 None — "new Slot(", "Slot.of(", "x = Slot(" 따위는 여기서 빠진다
def constructor_prefix(tokens, i):
    modifiers, annotations = set(), set()
    j = i - 1
    while j >= 0:
        text = tokens[j][0]
        if text in ACCESS:
            modifiers.add(text)
            j -= 1
            continue
        if text == ">":
            level = 0
            while j >= 0:
                if tokens[j][0] == ">":
                    level += 1
                elif tokens[j][0] == "<":
                    level -= 1
                    if level == 0:
                        break
                j -= 1
            j -= 1
            continue
        annotation = annotation_start(tokens, j)
        if annotation is not None:
            annotations.add(annotation[1])
            j = annotation[0] - 1
            continue
        break
    if j >= 0 and tokens[j][0] not in "{};":
        return None
    return "private" in modifiers, annotations


# 손으로 쓴 생성자 찾기: 이름 있는 타입(class·enum)의 본문 바로 아래 깊이에서 "[애너테이션] [접근자] [<타입 매개변수>] 타입이름(".
# 줄이 아니라 토큰 단위로 괄호 깊이를 따라가므로 같은 줄 애너테이션, 한 줄 선언(static class Y { Y() { } }),
# 제네릭 생성자, 중첩·깊은 중첩·지역 타입도 본다. 메서드 안(호출·new)·익명 클래스·람다 본문은 깊이가 달라 보지 않는다.
# record 는 dto 규칙(check-records.sh)이 따로 보므로 제외한다. 결과: (줄 번호 0부터, 타입이 예외 클래스인지, private, 애너테이션들)
def hand_constructors(code):
    tokens = list(tokens_with_lines(code))
    found = []
    depth = 0
    pending = None  # 선언은 봤고 본문 { 는 아직 — [이름, 종류, 예외 상속 여부]
    stack = []  # (이름, 종류, 본문 깊이, 예외 클래스인지)
    for i, (text, line) in enumerate(tokens):
        if text in TYPE_KEYWORDS and i + 1 < len(tokens) and re.match(r"[A-Za-z_$]", tokens[i + 1][0]) \
                and (i == 0 or tokens[i - 1][0] != "."):
            kind = "@interface" if i > 0 and tokens[i - 1][0] == "@" else text
            pending = [tokens[i + 1][0], kind, False, 0]
        elif pending and text == "<":
            pending[3] += 1
        elif pending and text == ">":
            pending[3] -= 1
        elif pending and pending[3] == 0 and text == "extends" and i + 1 < len(tokens) \
                and tokens[i + 1][0].endswith("Exception"):
            # 타입 매개변수 경계(<E extends Exception>)는 예외 상속이 아니다 — <> 바깥의 extends 만 본다
            pending[2] = True
        elif text == "{":
            depth += 1
            if pending:
                stack.append((pending[0], pending[1], depth, pending[2]))
                pending = None
        elif text == "}":
            if stack and stack[-1][2] == depth:
                stack.pop()
            depth -= 1
        elif text == ";":
            pending = None
        elif stack and stack[-1][2] == depth and stack[-1][1] in ("class", "enum") and text == stack[-1][0] \
                and i + 1 < len(tokens) and tokens[i + 1][0] == "(":
            prefix = constructor_prefix(tokens, i)
            if prefix:
                found.append((line, stack[-1][3], prefix[0], prefix[1]))
    return found


def check_file(path, is_test=False, safe_exceptions=frozenset()):
    raw = open(path, encoding="utf-8").read()
    source = code_only(raw)
    lines = source.split("\n")
    raw_lines = raw.split("\n")
    problems = []
    decl = TYPE_DECL.search(source)
    if not decl:
        if os.path.basename(path) in ("package-info.java", "module-info.java"):
            return problems
        return [(1, "타입 선언을 알아보지 못했습니다 — 이 파일의 규칙을 건너뛸 수 없으니 선언 모양을 고치거나 TYPE_DECL 을 보강하세요", "")]
    kind, name = decl.group(1), decl.group(2)

    # 1. 생성자 직접 작성 — 최상위·중첩·깊은 중첩 클래스와 enum 의 생성자. src/main 만.
    #    허용: private + @Builder, global/error 안에서 …Exception 을 상속한 타입 자신의 생성자(그 안의 중첩 비예외 타입은 검사)
    if not is_test:
        for i, is_exception, is_private, annotations in hand_constructors(source):
            if is_private and "Builder" in annotations:
                continue
            if is_exception and in_error_package(path):
                continue
            problems.append((i + 1, "생성자를 직접 썼습니다", raw_lines[i].strip()))

    # 2. if (!
    for i, line in enumerate(lines):
        if re.search(r"\bif\s*\(\s*!", line):
            problems.append((i + 1, "if (!…) 대신 '아니다' 함수를 쓰세요", line.strip()))

    # 3. 싱글톤 빈의 final 아닌 인스턴스 필드 (최상위 클래스 필드만: 들여쓰기 4칸) — src/main 만
    if STEREOTYPE.search(source) and not is_test:
        for i, line in enumerate(lines):
            if re.match(r"    (private|protected|public)?\s*(?!static\b)(?!final\b)[\w<>\[\], ?]+\s+\w+\s*(=[^;]*)?;\s*$", line) \
                    and not re.match(r"    (private|protected|public)?\s*(static|final)\b", line) \
                    and not re.match(r"    (return|throw)\b", line):
                if re.match(r"    \w", line) and "(" not in line.split("=")[0]:
                    problems.append((i + 1, "싱글톤 빈의 필드는 final 이어야 합니다(경쟁 조건 위험)", line.strip()))

    # 3-b. 싱글톤 빈의 final 필드에 바뀌는 그릇(맵·목록·집합·Atomic)을 담는 것 — 허용 목록만(사용자 결정 2026-10-28, 단위 10)
    if STEREOTYPE.search(source) and not is_test and name not in ALLOWED_STATEFUL_BEANS:
        for i, line in enumerate(lines):
            if MUTABLE_FIELD.match(line):
                problems.append((i + 1, "싱글톤 빈에 바뀌는 그릇을 두었습니다(허용 목록 밖 — 사용자에게 묻기)", line.strip()))

    # 4. setter — src/main 만. 외부 API 가 강제하는 재정의(@Override, 예: ServletInputStream.setReadListener)는 제외
    for i, line in enumerate(lines if not is_test else []):
        if re.search(r"@Setter\b", line):
            problems.append((i + 1, "setter 대신 change*/행위 메서드를 쓰세요", line.strip()))
        elif re.match(r"\s*public void set[A-Z]\w*\s*\(", line) and not is_override(lines, i):
            problems.append((i + 1, "setter 대신 change*/행위 메서드를 쓰세요", line.strip()))

    # 10. 가짜 구현체 위치 — 테스트의 Fake* 클래스는 test/fake 에만 둔다(상위 CLAUDE.md, 단위 9에서 어김)
    if is_test and name.startswith("Fake") and (PACKAGE_DIR + "fake/") not in path.replace(os.sep, "/"):
        problems.append((source[:decl.start()].count("\n") + 1, "가짜 구현체(Fake*)는 test/fake 패키지에 두세요", name))

    # 11. 프레임워크·외부 예외의 문장을 로그에 — src/main 만. 가장 가까운 앞 선언의 타입이 우리 예외(global/error)면 허용, 못 찾으면 위반
    if not is_test:
        offsets, total = [], 0
        for line in lines:
            offsets.append(total)
            total += len(line) + 1
        for i, end, statement in log_statements(lines):
            if message_leaks(statement, source, offsets[i], " ".join(raw_lines[i:end + 1]), safe_exceptions):
                problems.append((i + 1, "프레임워크 예외의 문장을 로그에 남기지 마세요(받은 값이 들어 있음) — 예외 종류 이름만", lines[i].strip()))

    # 5. 계층 방향
    layer = layer_of(path)
    if layer in LAYER_IMPORTS:
        allowed = LAYER_IMPORTS[layer] | (TEST_EXTRA if is_test else set())
        for i, line in enumerate(lines):
            imported = re.match(r"import (static )?" + re.escape(PACKAGE_ROOT) + r"\.(\w+)\.", line)
            if imported and imported.group(2) not in allowed:
                problems.append((i + 1, f"계층 방향 위반: {layer} → {imported.group(2)}", line.strip()))

    # 6. @Value
    for i, line in enumerate(lines):
        if re.search(r"@Value\s*\(", line):
            problems.append((i + 1, "@Value 대신 @ConfigurationProperties 를 쓰세요", line.strip()))

    # 7. 람다 안의 부정
    for i, line in enumerate(lines):
        if re.search(r"->\s*!", line):
            problems.append((i + 1, "람다 안의 ! 대신 Predicate.not(...)/isNotX 를 쓰세요", line.strip()))

    # 8. import 순서
    imports = [line for line in raw.split("\n") if line.startswith("import ")]
    statics = [line for line in imports if line.startswith("import static ")]
    normals = [line for line in imports if not line.startswith("import static ")]
    if imports != sorted(statics) + sorted(normals):
        problems.append((1, "import 순서: static 먼저, 그다음 일반, 각각 알파벳순", ""))
    return problems



def check_resources():
    problems = []
    if not os.path.isdir(RESOURCES):
        return problems
    for file in sorted(os.listdir(RESOURCES)):
        if not re.match(r"application.*\.(ya?ml|properties)$", file):
            continue
        path = os.path.join(RESOURCES, file)
        for i, line in enumerate(open(path, encoding="utf-8")):
            if line.lstrip().startswith("#"):
                continue
            for placeholder in re.findall(r"\$\{[^}:]+\}", line):
                problems.append((path, i + 1, "기본값 없는 자리표시자 — ${VAR:기본값} 으로 쓰고 필수 값은 @PostConstruct 에서 검사", placeholder))
    return problems


def main():
    found = 0
    safe_exceptions = frozenset(our_exceptions(MAIN))
    for base, is_test in ((MAIN, False), (TEST, True)):
        if not os.path.isdir(base):
            continue
        for directory, _, files in os.walk(base):
            for file in sorted(files):
                if not file.endswith(".java"):
                    continue
                path = os.path.join(directory, file)
                for line_no, message, code in check_file(path, is_test, safe_exceptions):
                    found += 1
                    print(f"   {os.path.relpath(path, ROOT)}:{line_no}: {message} — {code[:90]}")
    for path, line_no, message, code in check_resources():
        found += 1
        print(f"   {os.path.relpath(path, ROOT)}:{line_no}: {message} — {code}")
    if found:
        print(f"❌ 코드 규칙 위반 {found}건 (생성자 / if (!…) / 빈의 바뀌는 필드 / setter / 계층 방향 / @Value / 람다 ! / import 순서 / 설정 자리표시자 / Fake 위치 / 빈의 바뀌는 그릇 / 예외 문장 로그)")
        sys.exit(1)
    print("✅ 코드 규칙 — 생성자·if (!…)·빈의 바뀌는 필드·setter·계층 방향·@Value·람다 !·import 순서·설정 자리표시자·Fake 위치·빈의 바뀌는 그릇·예외 문장 로그 모두 0")


if __name__ == "__main__":
    main()
