-- 마지막 게시 기록(2시간마다 실행, 화면이 바뀔 때만 게시 — 사용자 승인 2026-11-01. 결정 대기: 기획서 5장에 없는 저장 구조).
-- 한 줄만 둔다(id = 1). push 가 성공한 뒤에만 바꾼다.
-- published_on: 게시한 한국 날짜(그날 첫 게시 판정), fingerprint: 보이는 지문(SHA-256 16진수)
create table publish_state (
  id           smallint primary key default 1 check (id = 1),
  published_on date not null,
  fingerprint  varchar(64) not null,
  published_at timestamptz not null
);
