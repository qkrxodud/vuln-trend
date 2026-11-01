-- 사이트 분류(사용자 결정 2026-11-01. 결정 대기: 기획서 5장에 없는 열).
-- attack_types: 공격 유형 키(AttackType.getKey, 선언 순서). 정기 실행이 NVD 약점(CWE)으로 매번 다시 매긴다. 분류 전이면 빈 배열
-- product_family: 제품군 키(백엔드 B 가 채운다). 모르면 비어 있다
alter table vulnerability
  add column attack_types   text[] not null default '{}',
  add column product_family varchar(20);
