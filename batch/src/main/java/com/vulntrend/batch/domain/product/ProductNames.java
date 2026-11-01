package com.vulntrend.batch.domain.product;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.Builder;

/**
 * 사이트에 보이는 제품 이름(B2, 사용자 승인 2026-11-01). 저장 값(primary_product·affected_product.product)은 원문 그대로 두고
 * 출력(index·recent·cve 상세·search-index)에서만 이 규칙 하나로 바꾼다.
 * - 표(product-alias)의 표시 이름이면 표에 적힌 대로(소문자 이름 curl·nginx 포함)
 * - 사람이 적은 이름(대문자나 띄어쓰기가 있음 — KEV 제품 이름 등)은 그대로
 * - 그 밖의 CPE 원문은 readable: 밑줄은 띄어 쓰고("_-_" 는 " - "), 단어 첫 글자를 대문자로, 알려진 약어와
 *   글자·숫자가 섞인 모델명은 대문자로. 하이픈·빗금·쌍점으로 이은 단어는 조각마다 같은 규칙, 마침표·문장 부호는 그대로.
 *   첫 단어가 아닌 짧은 이음말(for·of·and 등)은 소문자로 둔다. 제조사는 붙이지 않는다
 */
public class ProductNames {

    private static final Pattern WRITTEN_BY_PERSON = Pattern.compile("[A-Z\\s]");
    private static final Pattern MODEL = Pattern.compile("(?=.*[a-z])(?=.*\\d).+");
    private static final Pattern JOINERS = Pattern.compile("(?<=[-/:])|(?=[-/:])");
    private static final Set<String> ABBREVIATIONS = Set.of("AI", "AIX", "API", "ASA", "BIOS", "CLI", "CMS", "CPU",
            "CRM", "DNS", "EMUI", "ERP", "EUS", "FTP", "HP-UX", "HPC", "HTTP", "HTTPS", "IMAP", "IOS", "IP", "JSON",
            "LDAP", "NAS", "NX-OS", "OPC", "OS", "PDF", "PHP", "QTS", "RPC", "SAP", "SD-WAN", "SDK", "SMB", "SMTP",
            "SNMP", "SOA", "SQL", "SSH", "SSL", "TLS", "UA", "UEFI", "UI", "URL", "VM", "VPN", "XE", "XML", "XR");
    private static final Set<String> SMALL_WORDS = Set.of("and", "by", "for", "in", "of", "on", "or", "the", "to",
            "with");

    private final Set<String> standard;

    @Builder(access = AccessLevel.PRIVATE)
    private ProductNames(Set<String> standard) {
        this.standard = standard;
    }

    /** standardNames: 표(product_alias)의 표시 이름 전부 */
    public static ProductNames of(Collection<String> standardNames) {
        return ProductNames.builder().standard(Set.copyOf(standardNames)).build();
    }

    /** 저장된 제품 이름(비어 있지 않음)을 사이트에 보일 이름으로 */
    public String shown(String stored) {
        return standard.contains(stored) ? stored : readable(stored);
    }

    /** CPE 제품 이름 원문을 사람이 읽는 이름으로. 사람이 적은 이름은 그대로 */
    public static String readable(String raw) {
        if (WRITTEN_BY_PERSON.matcher(raw).find()) {
            return raw;
        }
        List<String> words = Arrays.stream(raw.split("_+")).filter(word -> word.length() > 0).toList();
        return IntStream.range(0, words.size())
                .mapToObj(i -> i > 0 && SMALL_WORDS.contains(words.get(i)) ? words.get(i) : word(words.get(i)))
                .collect(Collectors.joining(" "));
    }

    private static String word(String word) {
        String upper = word.toUpperCase(Locale.ROOT);
        if (ABBREVIATIONS.contains(upper)) {
            return upper;
        }
        return Arrays.stream(JOINERS.split(word)).map(ProductNames::piece).collect(Collectors.joining());
    }

    private static String piece(String piece) {
        String upper = piece.toUpperCase(Locale.ROOT);
        if (ABBREVIATIONS.contains(upper) || MODEL.matcher(piece).matches()) {
            return upper;
        }
        return piece.isEmpty() ? piece : piece.substring(0, 1).toUpperCase(Locale.ROOT) + piece.substring(1);
    }
}
