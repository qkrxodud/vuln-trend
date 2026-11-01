package com.vulntrend.batch.domain.category;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 약점 번호(NVD weaknesses 의 CWE) → 공격 유형(사용자 결정 2026-11-01). 한 CVE 에 여러 유형이 붙을 수 있다.
 * - 읽을 수 있는 번호가 없으면(약점 없음, NVD-CWE-noinfo·NVD-CWE-Other 뿐) 빈 목록 — 아직 분류 전이다
 * - 표에 없는 번호는 기타(OTHER). 표의 기타는 일부러 기타로 둔 것이다(너무 넓거나 공격 유형이 아닌 약점)
 * - 기타는 다른 유형이 하나도 없을 때만 붙는다(CWE-20 이 XSS 옆에 기타를 덧붙이지 않게)
 * - 최근 30일 상위 30개 약점은 모두 표에 있다(AttackTypesTest 가 고정, 2026-11-01 실데이터)
 * 표의 근거는 MITRE CWE 이름이다. 줄마다 대표 번호의 이름을 적는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AttackTypes {

    private static final Pattern CWE = Pattern.compile("CWE-(\\d+)");

    private static final Map<AttackType, Set<Integer>> TABLE = Map.of(
            // 94 Code Injection, 95 Eval Injection, 77·78 Command Injection, 502 Deserialization of Untrusted Data,
            // 917 Expression Language Injection, 1336 Template Injection, 470 Unsafe Reflection, 98 PHP Remote File Inclusion
            AttackType.RCE, Set.of(94, 95, 77, 78, 502, 917, 1336, 470, 98),
            // 74 Injection, 89 SQL, 77·78 Command, 94·95 Code, 88 Argument, 90 LDAP, 91 XML, 93 CRLF, 113 HTTP Response
            // Splitting, 643 XPath, 652 XQuery, 943 NoSQL·Data Query, 917 Expression Language, 1336 Template, 611 XXE
            AttackType.INJECTION, Set.of(74, 89, 77, 78, 94, 95, 88, 90, 91, 93, 113, 643, 652, 943, 917, 1336, 611),
            // 79 Cross-site Scripting, 80 Basic XSS, 83 Script in Attributes, 87 Alternate XSS Syntax
            AttackType.XSS, Set.of(79, 80, 83, 87),
            // 284 Access Control, 285 Authorization, 862 Missing Authorization, 863 Incorrect Authorization,
            // 639 Authorization Bypass Through User-Controlled Key, 287 Authentication, 306 Missing Authentication,
            // 288·290·294·302·305 Authentication Bypass, 307 Excessive Attempts, 269·266·250·268·270 Privilege,
            // 276·732 Permissions, 295 Certificate Validation, 345 Data Authenticity, 347 Signature Verification,
            // 384 Session Fixation, 425 Forced Browsing, 521·522·620·640 Credentials, 613 Session Expiration,
            // 798 Hard-coded Credentials, 648 Privileged APIs, 1390·1391 Weak·Default Authentication
            AttackType.AUTH, Set.of(284, 285, 862, 863, 639, 287, 306, 288, 290, 294, 302, 305, 307, 269, 266, 250,
                    268, 270, 276, 732, 295, 345, 347, 384, 425, 521, 522, 620, 640, 613, 798, 648, 1390, 1391),
            // 200 Exposure of Sensitive Information, 201 Through Sent Data, 203 Discrepancy, 204 Response Discrepancy,
            // 208 Timing, 209 Error Message, 212 Improper Removal, 215 Debugging Code, 312 Cleartext Storage,
            // 319 Cleartext Transmission, 359 Private Personal Information, 497 System Data, 532 Log File,
            // 538 Externally-Accessible File, 548 Directory Listing
            AttackType.INFO, Set.of(200, 201, 203, 204, 208, 209, 212, 215, 312, 319, 359, 497, 532, 538, 548),
            // 119 Buffer Bounds, 120 Classic Buffer Overflow, 121 Stack, 122 Heap, 124 Underwrite, 125 Out-of-bounds Read,
            // 126·127 Over·Under-read, 129 Array Index, 131 Buffer Size, 190·191 Integer Overflow·Underflow, 415 Double
            // Free, 416 Use After Free, 787 Out-of-bounds Write, 788·805 Access After End, 822·823·824·825 Pointer,
            // 843 Type Confusion, 680 Integer to Buffer Overflow
            AttackType.MEMORY, Set.of(119, 120, 121, 122, 124, 125, 126, 127, 129, 131, 190, 191, 415, 416, 787, 788,
                    805, 822, 823, 824, 825, 843, 680),
            // 400 Resource Consumption, 401 Memory Leak, 405 Asymmetric Consumption, 407 Algorithmic Complexity,
            // 409 Compressed Data, 476 NULL Pointer Dereference, 617 Reachable Assertion, 674 Recursion,
            // 770 Allocation Without Limits, 772 Missing Release, 789 Memory Allocation, 834·835 Loop, 1333 ReDoS,
            // 369 Divide By Zero, 1050 Excessive Platform Resource
            AttackType.DOS, Set.of(400, 401, 405, 407, 409, 476, 617, 674, 770, 772, 789, 834, 835, 1333, 369, 1050),
            // 22 Path Traversal, 23 Relative, 24·35 '../', 36 Absolute, 59 Link Following, 61 Symlink,
            // 73 External Control of File Name, 434 Unrestricted Upload of Dangerous File
            AttackType.PATH, Set.of(22, 23, 24, 35, 36, 59, 61, 73, 434),
            // 352 Cross-Site Request Forgery, 918 Server-Side Request Forgery, 441 Unintended Proxy
            AttackType.REQUEST_FORGERY, Set.of(352, 918, 441),
            // 일부러 기타: 20 Improper Input Validation(너무 넓다), 362·367 Race Condition, 601 Open Redirect,
            // 693 Protection Mechanism Failure, 1021 Clickjacking, 1188 Insecure Default, 116 Output Encoding,
            // 754 Unusual Conditions
            AttackType.OTHER, Set.of(20, 362, 367, 601, 693, 1021, 1188, 116, 754));

    private static final Map<Integer, Set<AttackType>> BY_WEAKNESS = invert(TABLE);

    /** 약점 번호 원문들("CWE-79" 등) → 공격 유형(겹치지 않게, 선언 순서). 읽을 수 있는 번호가 없으면 빈 목록 */
    public static List<AttackType> classify(List<String> cweIds) {
        Set<AttackType> types = EnumSet.noneOf(AttackType.class);
        cweIds.stream().map(AttackTypes::number).flatMap(Optional::stream)
                .forEach(number -> types.addAll(BY_WEAKNESS.getOrDefault(number, Set.of(AttackType.OTHER))));
        if (types.size() > 1) {
            types.remove(AttackType.OTHER);
        }
        return List.copyOf(types);
    }

    /** 표에 올라 있는 약점인가(표에 없어 기타로 떨어지는 것과 구분) */
    public static boolean isListed(String cweId) {
        return number(cweId).filter(BY_WEAKNESS::containsKey).isPresent();
    }

    /** 유형에 넣은 약점 번호들(보고·시험용) */
    public static Set<Integer> weaknessesOf(AttackType type) {
        return TABLE.get(type);
    }

    private static Optional<Integer> number(String cweId) {
        Matcher matcher = CWE.matcher(cweId.trim().toUpperCase(Locale.ROOT));
        if (matcher.matches()) {
            return Optional.of(Integer.valueOf(matcher.group(1)));
        }
        return Optional.empty();
    }

    private static Map<Integer, Set<AttackType>> invert(Map<AttackType, Set<Integer>> table) {
        Map<Integer, Set<AttackType>> inverted = new HashMap<>();
        table.forEach((type, numbers) -> numbers.forEach(
                number -> inverted.computeIfAbsent(number, ignored -> EnumSet.noneOf(AttackType.class)).add(type)));
        Map<Integer, Set<AttackType>> frozen = new HashMap<>();
        inverted.forEach((number, types) -> frozen.put(number, Collections.unmodifiableSet(types)));
        return Map.copyOf(frozen);
    }
}
