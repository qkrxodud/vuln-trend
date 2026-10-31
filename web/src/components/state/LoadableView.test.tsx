import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { LoadableView } from './LoadableView.tsx'

describe('데이터 영역의 불러오기 상태 표시', () => {
  it('아직 요청하지 않은 데이터는 아무것도 보이지 않는다', () => {
    const { container } = render(
      <LoadableView loadable={{ status: 'idle' }} label="통계">
        {() => <p>내용</p>}
      </LoadableView>,
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('불러오는 중에는 무엇을 불러오는지 화면 읽기 프로그램에도 알린다', () => {
    render(
      <LoadableView loadable={{ status: 'loading' }} label="통계">
        {() => <p>내용</p>}
      </LoadableView>,
    )

    expect(screen.getByRole('status')).toHaveTextContent('통계 불러오는 중')
    expect(screen.queryByText('내용')).not.toBeInTheDocument()
  })

  it('불러오지 못하면 무엇이 실패했는지 알리고, 다시 시도 버튼으로 다시 불러온다', async () => {
    const retry = vi.fn()
    render(
      <LoadableView loadable={{ status: 'error', error: new Error('down'), retry }} label="통계">
        {() => <p>내용</p>}
      </LoadableView>,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('통계를 불러오지 못했습니다')
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(retry).toHaveBeenCalledOnce()
  })

  it('받침으로 끝나는 이름도 조사를 맞춰 실패를 알린다', () => {
    render(
      <LoadableView loadable={{ status: 'error', error: new Error('down'), retry: vi.fn() }} label="트렌드 목록">
        {() => <p>내용</p>}
      </LoadableView>,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('트렌드 목록을 불러오지 못했습니다')
  })

  it('준비되면 받은 데이터로 내용을 보인다', () => {
    render(
      <LoadableView loadable={{ status: 'ready', data: 142 }} label="통계">
        {(count) => <p>신규 {count}건</p>}
      </LoadableView>,
    )

    expect(screen.getByText('신규 142건')).toBeInTheDocument()
  })
})
