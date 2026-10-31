// 데이터 파일을 받지 못했을 때의 오류. 어느 파일이 어떤 상태로 실패했는지 담는다
export class DataLoadError extends Error {
  readonly url: string
  /** HTTP 상태. 네트워크 자체가 실패했으면 `null` */
  readonly status: number | null

  constructor(url: string, status: number | null, options?: ErrorOptions) {
    super(`Failed to load ${url} (status: ${status ?? 'network error'})`, options)
    this.name = 'DataLoadError'
    this.url = url
    this.status = status
  }
}
