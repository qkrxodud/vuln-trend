// 정적 JSON 서버(GitHub Pages) 흉내. 등록한 URL 만 응답하고, 나머지는 404
interface FakeResponse {
  status: number
  body: unknown
}

export interface RecordedRequest {
  url: string
  cache: RequestCache | undefined
}

export class FakeFetch {
  readonly requests: RecordedRequest[] = []
  private readonly responses = new Map<string, FakeResponse[]>()

  /** url 에 JSON 을 둔다. 같은 url 에 여러 번 부르면 요청마다 차례로 응답한다 */
  serve(url: string, body: unknown, status = 200): this {
    const queue = this.responses.get(url) ?? []
    queue.push({ status, body })
    this.responses.set(url, queue)
    return this
  }

  requestsTo(url: string): RecordedRequest[] {
    return this.requests.filter((request) => request.url === url)
  }

  readonly fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = String(input)
    this.requests.push({ url, cache: init?.cache })
    const queue = this.responses.get(url) ?? []
    const next = queue.length > 1 ? queue.shift() : queue[0]
    if (next === undefined) return new Response('Not Found', { status: 404 })
    return new Response(JSON.stringify(next.body), { status: next.status })
  }
}
