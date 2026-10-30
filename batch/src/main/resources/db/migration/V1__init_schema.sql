-- 기획서 5장 스키마 그대로: 테이블 8개 + 인덱스 4개. 바꿀 때는 기획서 5장을 먼저 고친다.

create table vulnerability (
  vuln_id          varchar(20) primary key,   -- CVE-xxxx (GHSA-only 대비해 vuln_id)
  title            text,                      -- 3장 title 규칙으로 채움
  description      text,
  cvss_score       numeric(3,1),
  cvss_severity    varchar(10),               -- CRITICAL/HIGH/MEDIUM/LOW/UNRATED
  cvss_version     varchar(5),                -- 3.1 / 4.0
  epss_score       numeric(6,5),
  epss_percentile  numeric(6,5),
  is_kev           boolean default false,
  kev_added_at     date,
  kev_due_date     date,
  kev_ransomware   boolean default false,
  poc_count        int default 0,
  mention_count    int default 0,             -- 최근 7일 창 집계 (누적 아님)
  last_mention_at  timestamptz,
  primary_product  varchar(100),              -- product_alias 로 정규화된 display_name
  trend_score      numeric(8,2) default 0,
  published_at     timestamptz,
  last_modified_at timestamptz,
  rejected         boolean default false,
  updated_at       timestamptz default now()
);
create index on vulnerability (trend_score desc);
create index on vulnerability (published_at desc);

create table vulnerability_raw (      -- 소스별 원본, 재계산용
  vuln_id    varchar(20),
  source     varchar(20),
  payload    jsonb,
  fetched_at timestamptz,
  primary key (vuln_id, source)
);

create table affected_product (
  vuln_id   varchar(20) references vulnerability,
  vendor    varchar(100) not null,     -- OSV 패키지는 vendor 대신 ecosystem 값을 채움
  product   varchar(100) not null,
  ecosystem varchar(30),               -- OSV: Packagist, PyPI, npm, Maven, Go ...
  slug      varchar(100),              -- product_alias 로 정규화된 제품 slug
  vulnerable_range text,
  fixed_version    varchar(50),        -- MVP: CPE versionStart/End 로 range 만. fixed_version 은 OSV 수집 후
  primary key (vuln_id, vendor, product)
);
create index on affected_product (slug);
create index on affected_product (ecosystem);

create table product_alias (          -- CPE 제품명 정규화. 초기 데이터는 product-alias.yml(약 50개)
  cpe_vendor   varchar(100),
  cpe_product  varchar(100),
  slug         varchar(100) not null,
  display_name varchar(100) not null,
  primary key (cpe_vendor, cpe_product)
);

create table reference_link (
  vuln_id varchar(20) references vulnerability,
  url     text,
  source  varchar(20),                -- nvd / news / poc / vendor
  title   text,
  seen_at timestamptz,
  primary key (vuln_id, url)
);

create table summary (
  vuln_id      varchar(20) primary key references vulnerability,
  what         text,                  -- 무엇이 문제인가
  who          text,                  -- 누가 영향받나
  action       text,                  -- 뭘 해야 하나 (명령어 없음)
  model        varchar(50),
  generated_at timestamptz
);

create table daily_stat (
  stat_date       date primary key,    -- published_at 의 KST 달력일 (배치 실행일 아님)
  new_count       int,
  kev_new_count   int,
  critical_count  int,
  high_risk_count int,
  high_count      int,
  medium_count    int,
  low_count       int,
  unrated_count   int
);

create table sync_state (
  source         varchar(20) primary key,
  last_synced_at timestamptz,          -- 성공한 실행에서만 전진
  last_run_at    timestamptz,
  status         varchar(10)
);
