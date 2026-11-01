import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MINE_STORAGE_KEY } from '../lib/mine.ts'
import { MineProvider } from './MineProvider.tsx'
import { useMine } from './use-mine.ts'

const Probe = () => {
  const { mine, toggle, markSeen } = useMine()
  return (
    <>
      <output aria-label="내 제품">{mine.products.join(',')}</output>
      <output aria-label="본 때">{mine.seenAt ?? ''}</output>
      <button type="button" onClick={() => toggle('GitLab')}>
        GitLab 고르기
      </button>
      <button type="button" onClick={() => markSeen('2026-11-01T18:49:00+09:00')}>
        봤음
      </button>
    </>
  )
}

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('내 제품 저장 (이 브라우저에만)', () => {
  it('저장된 제품으로 시작하고, 고르면 바로 저장한다', () => {
    localStorage.setItem(MINE_STORAGE_KEY, JSON.stringify({ products: ['PHP'], seenAt: null }))
    render(
      <MineProvider>
        <Probe />
      </MineProvider>,
    )

    expect(screen.getByLabelText('내 제품')).toHaveTextContent('PHP')
    fireEvent.click(screen.getByRole('button', { name: 'GitLab 고르기' }))
    expect(screen.getByLabelText('내 제품')).toHaveTextContent('GitLab,PHP')
    expect(JSON.parse(localStorage.getItem(MINE_STORAGE_KEY) ?? '{}')).toEqual({
      products: ['GitLab', 'PHP'],
      seenAt: null,
    })
  })

  it('본 때를 남긴다', () => {
    render(
      <MineProvider>
        <Probe />
      </MineProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: '봤음' }))
    expect(screen.getByLabelText('본 때')).toHaveTextContent('2026-11-01T18:49:00+09:00')
    expect(JSON.parse(localStorage.getItem(MINE_STORAGE_KEY) ?? '{}').seenAt).toBe('2026-11-01T18:49:00+09:00')
  })

  it('저장소를 쓸 수 없어도(사생활 보호 모드 등) 이 화면 동안은 동작한다', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('denied')
    })
    render(
      <MineProvider>
        <Probe />
      </MineProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'GitLab 고르기' }))
    expect(screen.getByLabelText('내 제품')).toHaveTextContent('GitLab')
  })
})
