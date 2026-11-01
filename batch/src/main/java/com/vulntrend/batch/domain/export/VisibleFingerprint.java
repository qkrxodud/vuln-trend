package com.vulntrend.batch.domain.export;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 보이는 화면의 지문(2시간마다 확인·화면이 바뀔 때만 게시, 사용자 승인 2026-11-01). 화면 값 줄들을 순서대로 이어
 * SHA-256 16진수 64글자로 줄인다. 같은 줄이면 언제나 같은 지문, 줄 하나·순서 하나만 달라도 다른 지문이다.
 * 줄 끝마다 줄바꿈을 붙여 줄 경계가 지문에 들어간다("a","b" 와 "ab" 가 다르다)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VisibleFingerprint {

    private static final String ALGORITHM = "SHA-256";

    public static String of(List<String> lines) {
        MessageDigest digest = sha256();
        lines.forEach(line -> digest.update((line + "\n").getBytes(StandardCharsets.UTF_8)));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException missing) {
            throw new DomainValidationException("This Java runtime has no " + ALGORITHM + " digest.");
        }
    }
}
