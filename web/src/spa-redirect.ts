// 404.html 이 ?p= 로 실어 보낸 원래 경로를 주소창에 되돌린다. 라우터가 뜨기 전에 한 번 부른다
export const restoreSpaPath = (): void => {
  const params = new URLSearchParams(window.location.search)
  const path = params.get('p')
  if (path === null) return
  if (path.startsWith('/') === false) return

  const base = import.meta.env.BASE_URL.replace(/\/$/, '')
  window.history.replaceState(null, '', base + path + window.location.hash)
}
