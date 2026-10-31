import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SiteFooter } from './SiteFooter.tsx'

const renderFooter = () => {
  render(<SiteFooter />)
  return screen.getByRole('contentinfo')
}

describe('푸터 고지 (기획서 8장, D14)', () => {
  it('데이터 출처 네 곳을 각 사이트 링크로 밝힌다', () => {
    const footer = renderFooter()

    const sources = within(footer).getByRole('list', { name: '데이터 출처' })
    const links = within(sources).getAllByRole('link')
    expect(links.map((link) => [link.textContent, link.getAttribute('href')])).toEqual([
      ['NVD', 'https://nvd.nist.gov/'],
      ['CISA KEV', 'https://www.cisa.gov/known-exploited-vulnerabilities-catalog'],
      ['FIRST EPSS', 'https://www.first.org/epss/'],
      ['OSV.dev / GitHub Advisory Database', 'https://osv.dev/'],
    ])
  })

  it('출처 링크는 새 탭으로 열고 이 사이트 정보를 넘기지 않는다', () => {
    const footer = renderFooter()

    for (const link of within(footer).getAllByRole('link')) {
      expect(link).toHaveAttribute('target', '_blank')
      expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    }
  })

  it('NVD 가 요구하는 비인증 문구를 영어 원문 그대로 싣는다', () => {
    const footer = renderFooter()

    expect(footer).toHaveTextContent('This product uses the NVD API but is not endorsed or certified by the NVD.')
  })

  it('EPSS 는 FIRST 가 관리하고 점수는 Empirical Security 가 만든다고 출처를 밝힌다', () => {
    const footer = renderFooter()

    expect(footer).toHaveTextContent(/FIRST\.org.*Exploit Prediction Scoring System\(Jacobs et al\.\)/)
    expect(footer).toHaveTextContent(/점수는 Empirical Security 가 생성/)
  })

  it('CVE 가 MITRE 의 등록 상표임을 적는다', () => {
    const footer = renderFooter()

    expect(footer).toHaveTextContent('CVE는 The MITRE Corporation의 등록 상표입니다.')
  })

  it('정보 제공 목적이라 정확성을 보장하지 않으며 AI 요약은 원문을 확인하라고 알린다', () => {
    const footer = renderFooter()

    expect(footer).toHaveTextContent('정보 제공 목적이며 정확성·완전성을 보장하지 않습니다')
    expect(footer).toHaveTextContent('한국어 요약은 AI가 생성한 것으로 반드시 원문을 확인하세요')
  })
})
