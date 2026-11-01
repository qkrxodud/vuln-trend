// categories.json 시험용. 키·이름·순서는 배치 계약(08e39ac) 그대로
import type { Categories, Category } from '../types/categories.ts'

const ATTACK_TYPES: [string, string][] = [
  ['rce', '원격 코드 실행'],
  ['injection', '주입(SQL·명령)'],
  ['xss', '크로스 사이트 스크립팅'],
  ['auth', '인증·권한 우회'],
  ['info', '정보 노출'],
  ['memory', '메모리 손상'],
  ['dos', '서비스 거부'],
  ['path', '경로 조작·파일 업로드'],
  ['request_forgery', '요청 위조(CSRF·SSRF)'],
  ['other', '기타'],
]

const PRODUCT_FAMILIES: [string, string][] = [
  ['os', '운영체제'],
  ['browser', '브라우저'],
  ['network', '네트워크·보안 장비'],
  ['web_cms', '웹 CMS·플러그인'],
  ['server', '서버·DB·미들웨어'],
  ['dev', '개발 라이브러리·도구'],
  ['cloud', '클라우드·가상화'],
  ['ics_iot', '산업제어·IoT·펌웨어'],
  ['mobile', '모바일'],
  ['desktop', '업무용·데스크톱 앱'],
]

const empty = ([key, label]: [string, string]): Category => ({
  key,
  label,
  count_7d: 0,
  count_30d: 0,
  kev_count: 0,
  top: [],
})

/** 모든 키를 0건으로 싣고, overrides 의 키만 바꾼다 */
export const categoriesFixture = (overrides: Record<string, Partial<Category>> = {}): Categories => ({
  attack_types: ATTACK_TYPES.map((pair) => ({ ...empty(pair), ...overrides[pair[0]] })),
  product_families: PRODUCT_FAMILIES.map((pair) => ({ ...empty(pair), ...overrides[pair[0]] })),
})
