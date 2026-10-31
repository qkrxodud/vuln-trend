-- 한국어 요약 호출 기록(WBS 6.3, 결정 대기: 기획서 5장에 없는 저장 구조).
-- 하루 상한 20콜을 세고, 버리거나(명령어·지어낸 버전) 거절된 CVE 를 다시 부르지 않으려고 남긴다.
-- outcome: GENERATED / DISCARDED / REFUSED / FAILED. model 은 실제로 답한 모델(대체 모델일 수 있음), 실패면 비어 있다
create table summary_call (
  vuln_id   varchar(20) references vulnerability,
  called_at timestamptz not null,
  outcome   varchar(10) not null,
  model     varchar(50),
  primary key (vuln_id, called_at)
);
