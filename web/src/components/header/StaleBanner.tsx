// 갱신이 36시간을 넘었을 때의 안내. 배치 실패를 방문자가 알 수 있는 유일한 수단 (기획서 6장)
export const StaleBanner = () => (
  <div
    role="status"
    className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-xl border border-warn-line bg-warn-bg px-4 py-3 text-sm text-warn-ink"
  >
    <span className="font-bold">⚠ 데이터가 오래됐습니다.</span>
    <span>
      마지막 갱신이 36시간을 넘었습니다. 수집이 지연되고 있을 수 있으니 최신 정보는 NVD·CISA KEV 원문을 확인하세요.
    </span>
  </div>
)
