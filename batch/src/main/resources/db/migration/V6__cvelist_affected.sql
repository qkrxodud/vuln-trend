-- CVE.org cvelistV5 제품 보강(S3a, 사용자 승인 2026-11-02. 결정 대기: 기획서 5장에 없는 표).
-- cna_affected: 레코드의 containers.cna.affected[] 를 받은 순서(position)대로 그대로 담는다. 선정 규칙은 3b(백엔드 B)
create table cna_affected (
  vuln_id        varchar(20) references vulnerability,
  position       smallint,
  vendor         text,
  product        text,
  package_name   text,
  default_status varchar(20),
  versions       jsonb not null default '[]',   -- [{version, status, lessThan, lessThanOrEqual, versionType}]
  cpes           text[] not null default '{}',
  package_url    text,
  primary key (vuln_id, position)
);

-- cvelist_fetch: CVE 별 조회 상태. affected 를 채운 CVE 는 다시 부르지 않고, 못 채운 것은 attempted_at 에서 재시도 간격 뒤에 다시 부른다
create table cvelist_fetch (
  vuln_id      varchar(20) primary key references vulnerability,
  attempted_at timestamptz not null,
  answered_at  timestamptz,
  failures     int not null default 0
);
