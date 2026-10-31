import { withObjectParticle } from '../../lib/korean.ts'

// 불러오기 실패 안내 + 다시 시도
export const ErrorState = ({ label, onRetry }: { label: string; onRetry: () => void }) => (
  <div role="alert" className="flex items-center justify-between gap-4 rounded-md border border-gray-300 p-4 text-sm">
    <span>{withObjectParticle(label)} 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.</span>
    <button
      type="button"
      onClick={onRetry}
      className="rounded border border-gray-400 px-3 py-1 hover:bg-gray-100 focus-visible:outline-2"
    >
      다시 시도
    </button>
  </div>
)
