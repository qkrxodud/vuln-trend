-- 반영한 EPSS 점수 파일의 날짜(2시간마다 확인, 사용자 승인 2026-11-01, 결정 대기: 기획서 5장에 없는 저장 구조).
-- 단일 행. 같은 날짜의 파일을 다시 내려받거나 다시 반영하지 않으려고 남긴다. score_date 는 파일 첫 줄 주석의 score_date(UTC 날짜).
-- 번호: V3 은 백엔드 A 의 게시 기록(publish_state)
create table epss_file (
  id         smallint primary key default 1 check (id = 1),
  score_date date not null,
  applied_at timestamptz not null
);
