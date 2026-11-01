// v6 기호 세트 (design/v6/CONSENSUS.md 2절). 빨강 ◆ = CISA 조치 기한 D-0~3 하나, 항상 "D-n" 글자와 같이 쓴다
export const Diamond = ({ size = 'sm' }: { size?: 'sm' | 'lg' }) => (
  <span
    aria-hidden="true"
    className={`inline-block shrink-0 rotate-45 bg-urgent ${size === 'lg' ? 'mx-1 size-4' : 'mx-0.5 size-[9px]'}`}
  />
)

export const UrgentBadge = ({ dueInDays }: { dueInDays: number }) => (
  <span className="inline-flex items-center gap-[5px] rounded-full bg-urgent-wash px-[7px] py-[3px] text-xs leading-none font-semibold whitespace-nowrap text-urgent-ink">
    <Diamond />
    {`D-${dueInDays}`}
  </span>
)
