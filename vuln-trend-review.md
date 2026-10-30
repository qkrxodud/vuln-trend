# VulnTrend 기획서·디자인 리뷰 합의안

작성일: 2026-10-30
대상: `vuln-trend-plan.md`, Claude Design `VulnTrend Dashboard.dc.html`
작성: 리뷰 세션 2개(vuln-trend-77, vuln-trend-0e) 교차 검토 결과

상태 표기: ✅ 양쪽 합의 · ❓ 사용자 결정 필요

리뷰 세션 두 곳이 모든 항목에 합의했습니다. ❓ 표시 4건(4장)만 사용자 결정이 필요합니다.

---

## 1. 결정 사항 (합의)

### 1-1. 디자인 ↔ 기획서 충돌 해소

| # | 항목 | 결정 | 상태 |
|---|---|---|---|
| D1 | 우측 패널의 "영향 자산 1,284대" | 삭제. "영향 제품·버전"(OSV `fixed_version` 포함)으로 교체. 공개 사이트는 방문자 자산을 알 수 없음 | ✅ |
| D2 | "조치 기한 24시간" | CISA KEV `dueDate`(연방기관 패치 기한)로 매핑. 비KEV 건은 미표시 | ✅ |
| D3 | 조치 절차 체크리스트·진행률·"티켓 종료" | MVP 제외. 2단계에 localStorage 개인 체크리스트로 재검토 | ✅ (❓ 완전 삭제 vs 2단계) |
| D4 | 조치 절차의 SQL·PowerShell·gradle 명령어 | 생성하지 않음. 요약 `action`은 "버전 X 이상으로 업데이트" 수준 문장까지만. 벤더 어드바이저리 링크 필수, OSV 수정 버전 병기 | ✅ |
| D5 | 헤더 "실시간 수집 중" 펄스 + "N초 전" | 삭제. "마지막 갱신 10-30 09:00" 고정 표기. `meta.updatedAt`이 36시간 초과면 "데이터가 오래됐습니다" 배너(배치 실패 가시화 수단) | ✅ |
| D6 | 검색창·생태계 필터 부재 | 디자인에 추가. MVP 화면 범위에 검색 포함 | ✅ |
| D7 | "소프트웨어별 / 점수순" 보기 전환 | 채택. `index.json` 각 행에 `primary_product` 필요 | ✅ |
| D8 | 우측 sticky 패널 vs `/cve/` 상세 페이지 | 둘 다. 데스크톱은 패널에서 `cve/CVE-xxx.json` lazy fetch, URL도 `/cve/…`로 갱신. 모바일은 `/cve/` 페이지로 이동. 패널 하단 "상세 페이지 열기" 링크. 디자인에 모바일 레이아웃 1장 추가 | ✅ |
| D9 | "점수 구성" 막대(KEV/EPSS/CVSS 3개) | `score_breakdown`에 5항(KEV/EPSS/뉴스/PoC/CVSS)+감쇠를 모두 넣고, UI는 값이 0이 아닌 항만 표시(MVP에서는 뉴스·PoC가 0) | ✅ |
| D10 | "왜 지금?" 한 줄 | LLM 아닌 규칙 템플릿. KEV+PoC → "실제 악용 확인, 공개 PoC 존재" / KEV → "CISA 악용 목록 등재" / EPSS 상위 → "30일 내 악용 확률 상위 N%" | ✅ |
| D11 | KPI "+9 / 어제 133" 증감 | `meta.json`에 `yesterday` 블록 추가 | ✅ |
| D12 | KPI "PoC 공개" | PoC 탐지는 2단계라 MVP에서 0. MVP 4번째 KPI는 "고위험(EPSS 0.5 이상) 전체 건수 + 전일 대비 증감". 2단계에서 PoC로 교체 | ✅ |
| D13 | 모든 행의 한국어 한 줄 설명 | 요약을 "메인 상위 20건"으로 축소해 MVP에 포함(하루 20콜 이하). 나머지는 영어 원문 첫 문장 truncate | ✅ (❓ 비용 승인) |
| D14 | 푸터 부재 | 푸터 추가: 데이터 출처 4종, NVD 비인증 문구, EPSS/FIRST 인용, CVE 상표, 면책 | ✅ |
| D15 | 메인 목록 100건 | 20건 + "더 보기" | ✅ |

### 1-2. 기획서 보강

| # | 항목 | 결정 | 상태 |
|---|---|---|---|
| P1 | 시간 감쇠 | MVP 포함. 기준 시각 `max(published_at, kev_added_at, 최근 뉴스 언급)`에서 30일 반감기. 없으면 Log4Shell류가 영원히 1위 | ✅ |
| P2 | "오늘 신규" 정의 | "최근 24시간 신규": `published_at` 기준, 배치 실행 시각에서 24시간 역산. UTC 저장, 표시는 KST. KST 자정 기준 "오늘"은 09시 배치에서 거의 0건이라 부적합 | ✅ |
| P3 | `title` 채우기 | NVD에 title 없음. GHSA summary > 요약 `what` 첫 줄 > description 첫 문장 80자 | ✅ |
| P4 | NVD `vulnStatus = Rejected` | 수집 시 제외 | ✅ |
| P5 | KEV `knownRansomwareCampaignUse` | "랜섬웨어" 배지 + 점수 +10 | ✅ |
| P6 | 레포 성장(연 2~3만 파일) | 확정: (1) per-CVE JSON은 index/recent/products 목록 등장 건 + 점수 상위 N건만 생성(상한 수천 개), 나머지 검색 결과는 NVD 링크 (2) 데이터 500MB 초과 시 orphan 브랜치로 히스토리 재생성. 별도 레포 `vuln-trend-data` 분리는 사용자 결정(Q1) | ✅ (❓ Q1) |
| P7 | 분석 도구 | GoatCounter 또는 Plausible. 쿠키 없음 → 동의 배너 불필요 | ✅ (❓ 도입 여부) |
| P8 | `vite base` 고정값 | `VITE_BASE` 환경변수. 도메인 연결 시 `/`로 전환 | ✅ |
| P9 | LLM 요약 가드 | "원문에 없는 버전·제품 생성 금지" 프롬프트 가드, 사용 모델·일 호출 상한 명시, 면책 문구 + 원문 링크 병기 | ✅ |
| P10 | EPSS percentile | score와 percentile 둘 다 저장. UI는 "0.91 (상위 3%)" 병기 | ✅ |
| P11 | `primary_product` 선정 규칙 | KEV vendorProject/product > OSV package(ecosystem 포함) > CPE 첫 항목. `product_alias` 테이블(cpe_vendor, cpe_product → slug, display_name), 초기 데이터는 레포 내 YAML로 수동 관리(약 50개). 미매핑 제품은 CPE product 원문 노출, 메인 상위에 올라온 건만 2주 튜닝 루틴에서 매핑 추가 | ✅ |
| P12 | GHSA-only 건 | MVP 제외로 명시. 단 PK 컬럼명은 지금 `vuln_id`로 바꿔 둠(지금은 비용 0, 나중엔 마이그레이션) | ✅ |
| P13 | `affected_product` PK | OSV 패키지는 vendor null → PK 실패. OSV 건은 vendor에 ecosystem 값을 채움 | ✅ |
| P14 | CVSS 없는 건(Awaiting Analysis) | 심각도 도넛에 "미평가" 범주 | ✅ |
| P15 | `mention_count` | 최근 7일 창으로 집계, 누적 금지(2단계 점수식) | ✅ |
| P16 | GitHub Pages 캐시 | `meta.json`은 no-cache fetch, 나머지는 `meta.version`으로 `?v=` 캐시 버스팅 | ✅ |
| P17 | 배포 방식 이중 기재 | 9장 gh-pages 브랜치 / 10장 Actions → Actions 하나로 통일 | ✅ |
| P18 | 배치 부분 실패 | 모든 수집 단계 성공 시만 export + push. 실패한 소스의 `sync_state` 커서는 전진시키지 않아 다음 실행에서 재시도. 반쪽 JSON push 금지 | ✅ |
| P19 | `search-index.json` | 1년치 3~4MB. 검색창 포커스 시 lazy load | ✅ |
| P20 | 요약 JSON 구조 | `{what, who, action, why_now}` | ✅ |
| P21 | 상세 하단 "같은 제품 최근 5건" | "같은 패치로 함께 해결되는 CVE 묶음"으로 검토(2단계, 벤더 어드바이저리와 함께) | ✅ |
| P22 | 점수식 버전 | MVP(KEV+EPSS+CVSS+감쇠)와 2단계(뉴스·PoC 추가) 두 버전 병기 | ✅ |
| P23 | OG 메타태그 | 사이트 수준 OG 태그 MVP, CVE별은 7단계 SEO와 함께 | ✅ |

---

## 2. 기획서 수정 항목 (장별)

- **3장 데이터 소스**: GHSA-only MVP 제외 명시(P12). KEV `dueDate`, `knownRansomwareCampaignUse` 활용 추가(D2, P5). EPSS percentile 저장(P10). Rejected 제외(P4)
- **5장 스키마**: PK를 `vuln_id`로(P12), `epss_percentile`, `kev_due_date`, `kev_ransomware`, `primary_product`, `title` 규칙(P3), `product_alias` 테이블(P11), `affected_product` PK 수정(P13), `summary` 테이블을 what/who/action/why_now 컬럼으로(P20)
- **6장 배치**: 감쇠 포함 점수식 MVP/2단계 두 버전(P1, P22), mention 7일 창(P15), 실패 시 push 금지(P18), 요약 범위 상위 20건(D13)
- **7장 JSON**: `meta.json`에 `yesterday`, `version`(D11, P16), `index.json`에 `primary_product`, `score_breakdown`, `why_now`(D7, D9, D10), per-CVE 생성 범위 제한(P6), search-index lazy(P19)
- **8장 노출**: 검색창·필터, 패널+상세 이중 구조, 모바일, 푸터 고지, stale 배너, KPI 4번 교체, 목록 20건(D5~D8, D12, D14, D15), 미평가 범주(P14)
- **9장 로드맵**: 감쇠·요약 20건·푸터·분석 도구를 MVP로 이동. 배포 방식 Actions 통일(P17)
- **11장 운영**: LLM 가드·비용 상한·면책(P9), 레포 성장 전략(P6), 분석 도구(P7), `VITE_BASE`(P8)

## 3. 디자인 수정 항목

1. 우측 패널: 영향 자산 → 영향 제품·버전, 조치 기한 → KEV dueDate(비KEV 숨김), 체크리스트·진행률·명령어 블록 삭제, "조치" 1~3줄 + 링크로 교체
2. 헤더: 펄스·초 카운터 삭제, 고정 갱신 시각, stale 배너 상태 1장
3. 상단에 검색창 + 생태계 드롭다운 추가
4. KPI 4번: "PoC 공개" → "고위험(EPSS 0.5+)"
5. 도넛에 "미평가" 범주
6. 목록 20건 + "더 보기"
7. 패널 하단 "상세 페이지 열기" 링크, 모바일 레이아웃 1장
8. 푸터(출처·고지·면책)
9. EPSS 표기 "0.91 (상위 3%)"

## 4. 사용자 결정 필요 항목

| # | 질문 | 추천 |
|---|---|---|
| Q1 | 데이터를 별도 레포(`vuln-trend-data`)로 분리할지, 단일 레포 유지(파일 상한 + orphan 재생성은 어느 쪽이든 적용)할지 | 리뷰 세션 간 의견 갈림. vuln-trend-77: 분리(코드 빌드와 데이터 push 분리). vuln-trend-0e: 1차는 단일 레포 + 파일 상한, 필요 시 분리 |
| Q2 | 조치 체크리스트를 2단계 localStorage로 미룰지, localStorage로 MVP에 넣을지, 완전히 뺄지 | 2단계로 미룸. 디자인 자산은 보존 |
| Q3 | 분석 도구(GoatCounter/Plausible) 도입 여부 | 도입. 가중치 튜닝의 유일한 정량 피드백 |
| Q4 | 한국어 요약(상위 20건)을 MVP에 넣어 LLM 비용을 1차 공개부터 쓸지 | 포함. 하루 20콜 이하로 비용 미미, 차별점의 핵심 |

---

## 5. 검토 보충 (vuln-trend-0e 추가, 합의 범위 내 소항목)

| # | 항목 | 결정 |
|---|---|---|
| S1 | KPI·차트 집계 창 통일 | (v2 대조 리뷰로 수정: KPI 4개만 24시간 롤링 창, daily_stat·막대·도넛은 published_at의 KST 달력일 기준이며 매 실행 최근 14일 재계산. 하루 2회 실행 시 롤링 창이 이웃 날과 겹치기 때문) 원안: P2의 24시간 창을 KPI 4개에 동일 적용: "KEV 신규" = `kev_added_at`이 24시간 이내, "Critical" = 24시간 신규 중 CVSS 9.0 이상, "고위험" = 전체 중 EPSS 0.5 이상(전일 대비). 도넛 "이번 주" = 최근 7일 신규(ISO 주 아님). 막대 x축 날짜 라벨은 월 포함(`10/30`), 디자인의 "10월 N일" 하드코딩 제거 |
| S2 | CVSS 선택 우선순위 | NVD는 같은 CVE에 NVD·CNA 두 벤더의 v3.1과 v4가 함께 올 수 있음. 표시·점수용 CVSS는 NVD v3.1 > CNA v3.1 > v4 순. v4만 있으면 v4를 표시하고 라벨에 버전 표기 |
| S3 | 접근성 | 심각도·KEV·PoC를 색만으로 구분하지 않고 텍스트 배지 유지(디자인은 이미 충족). 막대·도넛 툴팁이 hover 전용이라 키보드 포커스로도 열리게. 상세 패널 열림을 스크린리더에 알리는 `aria-live` 1개 |

---

## 6. 사용자 결정 결과 (2026-10-30)

| # | 결정 |
|---|---|
| Q1 | 단일 레포 유지. 파일 상한 + orphan 재생성 적용. 별도 데이터 레포 분리 안 함 |
| Q2 | 조치 체크리스트는 2단계(localStorage)로 미룸. 디자인 자산 보존 |
| Q3 | 분석 도구 도입하지 않음 |
| Q4 | 한국어 요약 상위 20건을 MVP에 포함 |

반영: `vuln-trend-plan.md` 전면 개정(vuln-trend-0e 수행).

---

## 7. v2 대조 리뷰 수정 (vuln-trend-77 지적, vuln-trend-0e 반영)

| # | 수정 |
|---|---|
| R1 | MVP 필터를 전체 / KEV / Critical 하나로 통일. 생태계·PoC 필터는 MVP 제외 목록으로 |
| R2 | KPI만 24시간 롤링, daily_stat·차트는 KST 달력일 + 최근 14일 재계산 (S1 수정) |
| R3 | 10장 테이블 수 9 → 8 |
| R4 | MVP 버전 범위는 NVD CPE configurations, 벤더 링크는 NVD references 태그(Vendor Advisory/Patch)에서. OSV·어드바이저리 수집 전 출처 명시 |
| R5 | rejected 건의 per-CVE 파일 삭제와 목록·search-index 제외를 7장·10장에 명시 |
| R6 (선택) | trend_score는 JSON에 정수 반올림, breakdown은 소수 1자리로 출력해 일일 diff churn 축소 |
