import { useId, useState, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router'
import { useSearchIndex } from '../../hooks/use-data.ts'
import { findDirectCveId, searchEntries } from '../../lib/search.ts'
import type { SearchEntry } from '../../types/search.ts'
import { ErrorState } from '../state/ErrorState.tsx'

const SEARCH_LABEL = 'CVE-ID 또는 제품명 검색'

interface SearchOption {
  id: string
  title: string
  product: string | null
  /** 색인에 없지만 완전한 CVE 번호를 적은 경우의 바로 가기 */
  direct: boolean
}

const toOption = (entry: SearchEntry): SearchOption => ({ ...entry, direct: false })

const optionsFor = (index: SearchEntry[], query: string): { options: SearchOption[]; total: number } => {
  const { matches, total } = searchEntries(index, query)
  const options = matches.map(toOption)
  const directId = findDirectCveId(query)
  if (directId === null) return { options, total }
  if (matches.some((entry) => entry.id === directId)) return { options, total }
  return { options: [...options, { id: directId, title: '', product: null, direct: true }], total }
}

// 머리글 검색창. 처음 들어갈 때 search-index.json 을 받고(P19), 결과를 고르면 상세 페이지로 간다 (WBS 5.9)
// 키보드: ↓↑ 로 결과 이동, Enter 로 고르기, Esc 로 닫기 (WAI-ARIA combobox)
export const SearchBox = () => {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const [requested, setRequested] = useState(false)
  const searchIndex = useSearchIndex(requested)
  const navigate = useNavigate()
  const listboxId = useId()

  const hasQuery = query.trim() !== ''
  const { options, total } =
    searchIndex.status === 'ready' ? optionsFor(searchIndex.data, query) : { options: [], total: 0 }
  const expanded = open && hasQuery
  const hiddenCount = total - options.filter((option) => option.direct === false).length
  const optionId = (index: number) => `${listboxId}-option-${index}`

  const choose = (option: SearchOption) => {
    navigate(`/cve/${option.id}`)
    setQuery('')
    setOpen(false)
    setActiveIndex(-1)
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      setOpen(false)
      setActiveIndex(-1)
      return
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setOpen(true)
      setActiveIndex((current) => Math.min(options.length - 1, current + 1))
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((current) => Math.max(0, current - 1))
      return
    }
    if (event.key !== 'Enter') return
    const option = options[activeIndex] ?? options[0]
    if (option === undefined) return
    event.preventDefault()
    choose(option)
  }

  return (
    <div className="relative max-w-[520px] flex-[1_1_320px]">
      <div className="flex items-center gap-2 rounded-xl border border-border bg-surface-sunk px-3 py-2 focus-within:border-ink-2 focus-within:bg-white">
        <span className="text-sm text-ink-3" aria-hidden="true">
          ⌕
        </span>
        <input
          type="search"
          role="combobox"
          aria-label={SEARCH_LABEL}
          aria-expanded={expanded}
          aria-controls={listboxId}
          aria-autocomplete="list"
          aria-activedescendant={expanded && activeIndex >= 0 ? optionId(activeIndex) : undefined}
          value={query}
          placeholder={SEARCH_LABEL}
          onFocus={() => {
            setRequested(true)
            setOpen(true)
          }}
          onBlur={() => setOpen(false)}
          onChange={(event) => {
            setQuery(event.target.value)
            setOpen(true)
            setActiveIndex(-1)
          }}
          onKeyDown={onKeyDown}
          className="flex-1 bg-transparent text-sm text-ink outline-none [&::-webkit-search-cancel-button]:hidden"
        />
        {query === '' ? null : (
          <button
            type="button"
            onClick={() => setQuery('')}
            aria-label="검색 지우기"
            className="rounded-full bg-surface-sunk px-2 py-0.5 text-xs text-ink-2"
          >
            지우기
          </button>
        )}
      </div>
      {expanded ? (
        // 누르는 동안 입력창 포커스를 지켜 결과 목록이 먼저 닫히지 않게 한다
        <div
          onMouseDown={(event) => event.preventDefault()}
          className="absolute top-full right-0 left-0 z-20 mt-1 max-h-96 overflow-auto rounded-xl border border-border bg-white p-1.5 text-sm shadow-[0_6px_24px_rgba(20,20,30,.08)]"
        >
          {searchIndex.status === 'loading' || searchIndex.status === 'idle' ? (
            <p role="status" className="px-3 py-2 text-ink-3">
              검색 색인 불러오는 중…
            </p>
          ) : null}
          {searchIndex.status === 'error' ? <ErrorState label="검색 색인" onRetry={searchIndex.retry} /> : null}
          {searchIndex.status === 'ready' && options.length === 0 ? (
            <p role="status" className="px-3 py-2 text-ink-3">
              "{query.trim()}"에 맞는 취약점이 없습니다.
            </p>
          ) : null}
          {options.length > 0 ? (
            <ul id={listboxId} role="listbox" aria-label="검색 결과">
              {options.map((option, index) => (
                <li
                  key={option.id}
                  id={optionId(index)}
                  role="option"
                  aria-selected={index === activeIndex}
                  data-cve-id={option.id}
                  onClick={() => choose(option)}
                  className={`flex cursor-pointer flex-col gap-0.5 rounded-lg px-3 py-2 ${
                    index === activeIndex ? 'bg-surface-sunk' : 'hover:bg-surface-sunk'
                  }`}
                >
                  {option.direct ? (
                    <span>
                      <span className="font-mono font-semibold">{option.id}</span> 바로 가기
                    </span>
                  ) : (
                    <>
                      <span className="flex items-center gap-2">
                        <span className="font-mono font-semibold">{option.id}</span>
                        {option.product === null ? null : <span className="text-xs text-ink-3">{option.product}</span>}
                      </span>
                      <span className="truncate text-ink-2">{option.title}</span>
                    </>
                  )}
                </li>
              ))}
            </ul>
          ) : null}
          {hiddenCount > 0 ? (
            <p className="px-3 py-1.5 text-xs text-ink-3">외 {hiddenCount}건 — 검색어를 더 구체적으로 적어 보세요</p>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
