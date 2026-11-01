import { nvdUrl } from '../../lib/cve-detail.ts'

// 상세 JSON 을 만들지 않은 CVE (P6): 이유와 NVD 원문
export const NoDetail = ({ cveId }: { cveId: string }) => (
  <div className="flex flex-col gap-3 text-sm text-ink-2">
    <p className="m-0">
      이 취약점은 상세 정보를 따로 만들지 않았습니다. 상세는 트렌드 상위·최근 7일 목록에 오른 취약점만 만듭니다.
    </p>
    <p className="m-0 flex items-baseline gap-2">
      <span className="w-11 shrink-0 text-xs text-ink-3">NVD</span>
      <a href={nvdUrl(cveId)} target="_blank" rel="noopener noreferrer" className="text-ink hover:underline">
        nvd.nist.gov<span className="sr-only"> (새 창)</span> ↗
      </a>
    </p>
  </div>
)
