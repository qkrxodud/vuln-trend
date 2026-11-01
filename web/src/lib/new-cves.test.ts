import { describe, expect, it } from 'vitest'
import { trendRow } from '../test/rows.ts'
import {
  ALL_NEW,
  dateOptions,
  filterNew,
  hasFixInfo,
  isFiltered,
  productSuggestions,
  publishedDate,
  sortNew,
} from './new-cves.ts'

const rows = [
  trendRow({
    id: 'a',
    score: 10,
    severity: 'CRITICAL',
    primary_product: 'GitLab',
    published_at: '2026-11-01T08:00:00+09:00',
    fix_versions: ['19.4.2'],
  }),
  trendRow({
    id: 'b',
    score: 90,
    severity: 'HIGH',
    primary_product: 'Apache Tomcat',
    is_kev: true,
    kev_due_date: '2026-11-20',
    published_at: '2026-10-31T23:30:00+09:00',
    patch_url: 'https://x.example/p',
  }),
  trendRow({ id: 'c', score: 50, severity: 'LOW', primary_product: null, published_at: '2026-10-31T15:30:00Z' }),
  trendRow({
    id: 'd',
    score: 70,
    severity: 'UNRATED',
    primary_product: 'gitlab runner',
    published_at: '2026-10-30T10:00:00+09:00',
  }),
]

describe('조치 정보·공개일', () => {
  it('수정 버전이나 패치 링크 중 하나라도 있으면 조치 정보가 있다', () => {
    expect(rows.map(hasFixInfo)).toEqual([true, true, false, false])
  })

  it('공개일은 한국 달력일로 본다 (UTC 15:30 은 다음 날)', () => {
    expect(publishedDate(rows[2])).toBe('2026-11-01')
    expect(publishedDate(trendRow({ published_at: undefined }))).toBeNull()
  })
})

describe('거르기', () => {
  it('아무것도 고르지 않으면 모두 보인다', () => {
    expect(filterNew(rows, ALL_NEW).map((row) => row.id)).toEqual(['a', 'b', 'c', 'd'])
    expect(isFiltered(ALL_NEW)).toBe(false)
  })

  it('심각도는 여러 개를 고를 수 있다', () => {
    expect(filterNew(rows, { ...ALL_NEW, severities: ['CRITICAL', 'UNRATED'] }).map((row) => row.id)).toEqual([
      'a',
      'd',
    ])
  })

  it('KEV 만, 조치 정보 있음만 고를 수 있다', () => {
    expect(filterNew(rows, { ...ALL_NEW, kevOnly: true }).map((row) => row.id)).toEqual(['b'])
    expect(filterNew(rows, { ...ALL_NEW, fixOnly: true }).map((row) => row.id)).toEqual(['a', 'b'])
  })

  it('제품은 이름 일부로 대소문자 없이 찾고, 제품 미상도 찾을 수 있다', () => {
    expect(filterNew(rows, { ...ALL_NEW, product: 'GITLAB' }).map((row) => row.id)).toEqual(['a', 'd'])
    expect(filterNew(rows, { ...ALL_NEW, product: '제품 미상' }).map((row) => row.id)).toEqual(['c'])
  })

  it('공개일(한국 날짜)로 거른다', () => {
    expect(filterNew(rows, { ...ALL_NEW, date: '2026-11-01' }).map((row) => row.id)).toEqual(['a', 'c'])
  })

  it('조건을 겹쳐 쓸 수 있고, 하나라도 걸리면 거른 상태다', () => {
    const filter = { ...ALL_NEW, product: 'gitlab', fixOnly: true }

    expect(filterNew(rows, filter).map((row) => row.id)).toEqual(['a'])
    expect(isFiltered(filter)).toBe(true)
  })
})

describe('정렬', () => {
  it('최신순은 공개 시각 내림차순, 점수순은 점수 내림차순이다', () => {
    expect(sortNew(rows, 'latest').map((row) => row.id)).toEqual(['a', 'c', 'b', 'd'])
    expect(sortNew(rows, 'score').map((row) => row.id)).toEqual(['b', 'd', 'c', 'a'])
  })

  it('공개 시각이 비어(null) 있어도 날짜를 모르는 것으로 보고 멈추지 않는다', () => {
    const withNull = [...rows, trendRow({ id: 'n', published_at: null })]

    expect(publishedDate(withNull[4])).toBeNull()
    expect(sortNew(withNull, 'latest').map((row) => row.id)).toContain('n')
    expect(dateOptions(withNull).reduce((sum, option) => sum + option.count, 0)).toBe(4)
  })

  it('공개 시각이 없으면 최신순은 파일 순서(배치가 쓴 순서)를 그대로 둔다', () => {
    const plain = rows.map((row) => ({ ...row, published_at: undefined }))

    expect(sortNew(plain, 'latest').map((row) => row.id)).toEqual(['a', 'b', 'c', 'd'])
  })
})

describe('선택지', () => {
  it('날짜는 최근 날부터 건수와 함께, 공개 시각이 없으면 비운다', () => {
    expect(dateOptions(rows)).toEqual([
      { date: '2026-11-01', count: 2 },
      { date: '2026-10-31', count: 1 },
      { date: '2026-10-30', count: 1 },
    ])
    expect(dateOptions(rows.map((row) => ({ ...row, published_at: undefined })))).toEqual([])
  })

  it('제품 도움말은 건수 많은 순으로 상한만큼 준다', () => {
    const many = [...rows, trendRow({ primary_product: 'Apache Tomcat' })]

    expect(productSuggestions(many, 2)).toEqual(['Apache Tomcat', 'GitLab'])
  })
})
