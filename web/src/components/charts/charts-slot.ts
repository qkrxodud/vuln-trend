// 차트 줄 자리. 지연 로드(LazyChartsSection)·stats 불러오는 중·실패 때도 같은 높이를 잡아 아래 목록이 밀리지 않게 한다
// 값은 실제 차트 높이를 재서 맞춘 것 (2026-10-31, 640 미만 819 · 640~1023 607 · 1024 이상 318).
// 차트 머리말·범례·도넛 크기를 바꾸면 다시 재야 한다
export const CHARTS_LABEL = '추이 차트'
export const CHARTS_SLOT_CLASS = 'min-h-[819px] sm:min-h-[607px] lg:min-h-[318px]'
