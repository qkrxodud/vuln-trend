// 불러오는 중 안내. role=status 로 화면 읽기 프로그램에도 알린다
export const LoadingState = ({ label }: { label: string }) => (
  <div role="status" aria-live="polite" className="animate-pulse rounded-md bg-gray-100 p-4 text-sm text-gray-500">
    {label} 불러오는 중…
  </div>
)
