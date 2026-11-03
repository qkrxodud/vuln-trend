// Pages 배포(pages.yml)가 시험을 건너뛸지 정한다 (WBS §11a S1).
// 배치의 데이터 커밋(2시간마다)은 web/public/data 만 바꾼다. 그때는 시험 없이 빌드·배포만 하고, 사이트 코드가 섞이면 지금처럼 시험한다.
// 경로는 저장소 기준이다(git diff --name-only)
export const DATA_DIR = 'web/public/data/'
const SITE_DIR = 'web/'
const WORKFLOW_DIR = '.github/'

// 배치·문서처럼 사이트 빌드에 들어가지 않는 파일. 워크플로는 배포 방식이 바뀌므로 사이트 쪽으로 친다
const isOutsideSite = (file: string) => file.startsWith(SITE_DIR) === false && file.startsWith(WORKFLOW_DIR) === false

/** 바뀐 파일을 모르면(빈 목록) 데이터만이라고 보지 않는다 → 시험을 돌린다 */
export const isDataOnlyChange = (files: string[]): boolean => {
  const changed = files.map((file) => file.trim()).filter((file) => file !== '')
  return changed.length > 0 && changed.every((file) => file.startsWith(DATA_DIR) || isOutsideSite(file))
}
