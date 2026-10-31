// 브라우저 matchMedia 흉내. jsdom 에는 없다. (min-width: Npx) 만 이해한다
type Listener = (event: MediaQueryListEvent) => void

const DESKTOP_WIDTH = 1280
let width = DESKTOP_WIDTH
const lists = new Set<{ query: string; listeners: Set<Listener> }>()

const matchesWidth = (query: string): boolean => {
  const minWidth = /min-width:\s*(\d+)px/.exec(query)
  return minWidth === null ? false : width >= Number(minWidth[1])
}

export const installFakeMedia = (): void => {
  width = DESKTOP_WIDTH
  window.matchMedia = (query: string) => {
    const entry = { query, listeners: new Set<Listener>() }
    lists.add(entry)
    return {
      get matches() {
        return matchesWidth(query)
      },
      media: query,
      onchange: null,
      addEventListener: (_type: string, listener: Listener) => entry.listeners.add(listener),
      removeEventListener: (_type: string, listener: Listener) => entry.listeners.delete(listener),
      addListener: (listener: Listener) => entry.listeners.add(listener),
      removeListener: (listener: Listener) => entry.listeners.delete(listener),
      dispatchEvent: () => true,
    } as unknown as MediaQueryList
  }
}

/** 화면 폭을 바꾸고, 결과가 바뀐 미디어 쿼리 구독자에게 알린다 */
export const setViewportWidth = (next: number): void => {
  width = next
  for (const entry of lists) {
    const event = { matches: matchesWidth(entry.query), media: entry.query } as MediaQueryListEvent
    entry.listeners.forEach((listener) => listener(event))
  }
}
