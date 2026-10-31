# ranking-sample.csv 다시 만들기 (WBS 3.5)

`TrendRankingSampleTest` 가 쓰는 순위 검증 표본이다. 실데이터에서 만들고, 만든 날을 실행 시각(2026-10-31T00:00:00Z)으로 고정한다.
다시 만들 때는 아래 순서를 따르고, 테스트의 실행 시각·최근 KEV 기준일도 새 날짜로 함께 옮긴다.

1. 받기 (내려받은 파일은 저장소 밖 빈 폴더에 둔다)
   - NVD API 2.0 `cves/2.0?pubStartDate=…&pubEndDate=…&resultsPerPage=2000&startIndex=…` — 실행일 전 한 달 발행분 전부, 그 전 몇 주치 표본
   - 최근 3~4개월 안에 KEV 에 오른 CVE 중 위에 없는 것, 유명 옛 건(CVE-2021-44228, CVE-2017-0144, CVE-2014-0160, CVE-2023-4966, CVE-2021-26855)은 `cves/2.0?cveId=…` 로 한 건씩 (키 없이 30초 5건, 6.5초 간격)
   - CISA KEV `known_exploited_vulnerabilities.json`, EPSS `epss_scores-current.csv.gz`
2. 잇기 (CVE 마다 한 줄)
   - 철회(Rejected) 건은 뺀다. CVSS 는 NVD 3.1 > CNA 3.1 > NVD 3.0 > CNA 3.0 > NVD 4.0 > CNA 4.0, 심각도는 9.0/7.0/4.0 경계, 점수가 없으면 UNRATED
   - KEV 는 등재일·랜섬웨어(`Known`), EPSS 는 점수·백분위
   - NVD 를 조회하지 않은 옛 KEV 건은 발행일을 비우고 CVSS 를 10.0 으로 둔다(가장 불리한 위쪽 값)
3. 줄이기
   - 트렌드 점수식(기획서 6장, 3.1 확정 규칙)으로 실행 시각 기준 점수를 매기고 100위 점수를 구한다
   - 감쇠 전 점수가 100위 점수 이상인 건과 유명 옛 건만 남긴다. 뺀 건이 모두 100위 점수 미만인지 확인한다(감쇠는 점수를 줄이기만 하므로 뺀 건은 100위에 들 수 없다)
4. 쓰기 — 머리말(`#` 줄)에 실행 시각·출처·판·줄인 방법을 적고, 열은 `vuln_id,published_at,cvss_score,cvss_severity,is_kev,kev_added_at,kev_ransomware,epss_score,epss_percentile`
