import { useState } from 'react'

const SEARCH_LABEL = 'CVE-ID 또는 제품명 검색'

// 검색창 자리. 검색 색인 lazy load 와 결과 목록은 5.9 에서 붙인다
export const SearchBox = () => {
  const [query, setQuery] = useState('')
  return (
    <div className="flex max-w-[520px] flex-[1_1_320px] items-center gap-2 rounded-xl border border-line bg-paper px-3 py-2 focus-within:border-accent focus-within:bg-white">
      <span className="text-sm text-ink-faint" aria-hidden="true">
        ⌕
      </span>
      <input
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder={SEARCH_LABEL}
        aria-label={SEARCH_LABEL}
        className="flex-1 bg-transparent text-sm text-ink outline-none [&::-webkit-search-cancel-button]:hidden"
      />
      {query === '' ? null : (
        <button
          type="button"
          onClick={() => setQuery('')}
          aria-label="검색 지우기"
          className="rounded-full bg-chip px-2 py-0.5 text-xs text-ink-soft"
        >
          지우기
        </button>
      )}
    </div>
  )
}
