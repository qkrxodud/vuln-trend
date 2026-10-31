# web/ 프론트 코드 규칙 (확정 2026-10-30)

작성: 프론트 개발자 세션, 2026-10-30, WBS 5.1. 사용자 확인으로 확정 2026-10-30.
루트 `CLAUDE.md` 의 언어 무관 규칙을 그대로 따르고, 아래는 `web/` 에만 적용한다.

## 1. 도구

| 도구     | 설정                                                             | 비고                                                                        |
| -------- | ---------------------------------------------------------------- | --------------------------------------------------------------------------- |
| 타입체크 | `npm run typecheck` (`tsc -b`)                                   | `tsconfig.app.json`(src)·`tsconfig.node.json`(vite.config·scripts)          |
| 린트     | `npm run lint` (oxlint, `.oxlintrc.json`)                        | 확정 2026-10-30: ESLint 대신 Vite 9 템플릿 기본 oxlint                      |
| 포맷     | `npm run format` / `format:check` (Prettier, `.prettierrc.json`) | 세미콜론 없음, 작은따옴표, 한 줄 120자. `public/data/` 는 제외(배치 산출물) |
| 빌드     | `npm run build`                                                  | 완료 조건: build·lint·format:check 셋 다 종료 코드 0                        |

## 2. 폴더 구조

```
web/
  public/data/        배치가 쓰는 JSON (M1 전에는 scripts/generate-mock-data.ts 의 목업)
  scripts/            개발용 Node 스크립트 (목업 생성)
  src/
    types/            JSON 계약 타입. 기획서 7장·WBS 4.2 와 1:1. 계약 변경은 오케스트레이터 경유
    api/              fetch 와 캐시 버스팅 (5.2)
    hooks/            데이터·상태 훅 (useMeta, useTrendIndex …)
    components/       화면 조각. 영역별 하위 폴더 (header/, kpi/, charts/, trend/, detail/, footer/)
    pages/            라우트 한 개 = 파일 한 개
    lib/              순수 함수 (날짜 KST 표기, 심각도 색, 점수 막대 계산). 단위 테스트 대상
```

## 3. 코드 규칙

1. **JSON 필드 이름을 바꾸지 않는다.** `types/` 의 이름(모두 snake_case: `is_kev`, `kev_new` …)을 그대로 쓰고 camelCase 로 바꾸는 변환층을 두지 않는다. 계약과 코드가 한눈에 대조되게 하려는 것이다.
2. **조건은 early return.** 중첩 조건은 함수로 나눈다. `if (!x)` 대신 `if (x === null)`·`if (x === false)` 처럼 비교를 드러낸다(CLAUDE.md 규칙 4 와 같은 취지).
3. **null 은 계약에 적힌 곳에만.** `undefined` 와 섞지 않는다. 화면 대체값(요약 없음 → title)은 `lib/` 의 함수 한 곳에서 정한다.
4. **컴포넌트는 named export + 화살표 함수.** default export 는 `App` 하나.
5. **import 는 확장자를 붙인다**(`./common.ts`). `scripts/` 가 Node 로 같은 타입을 import 하기 때문이다.
6. **주석은 한국어, 콘솔·오류 메시지는 영어.** 화면 문구는 한국어.
7. **색은 기획서 8장 색상 규칙만.** 심각도 5색·KEV 빨강·PoC 주황 외에는 회색. 색만으로 구분하지 않고 텍스트 배지를 함께 둔다.
8. **시각은 KST 고정 표기.** 브라우저 시간대에 기대지 않는다(`Intl.DateTimeFormat` + `timeZone: 'Asia/Seoul'`).
9. **테스트**: `lib/` 순수 함수는 Vitest 단위 테스트(5.2 에서 도입). 테스트 이름은 `domain-test-writing` 취지대로 도메인 문장으로 쓴다(예: "KEV 가 아니면 기한을 보이지 않는다").

## 4. 라우팅·배포 메모

- `vite.config.ts` 의 `base` = `VITE_BASE`(없으면 `/vuln-trend/`). 라우터 `basename` 은 `import.meta.env.BASE_URL`.
- GitHub Pages SPA: 빌드 때 `404.html` 을 생성해 없는 경로를 `?p=` 로 앱에 넘기고, `src/spa-redirect.ts` 가 원래 주소로 되돌린다.
