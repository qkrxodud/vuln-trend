// 목업 생성기가 실제 데이터를 덮어쓰지 않게 막는다 (M1 뒤 web/public/data 는 배치 산출물)
export interface MockOutputPlan {
  outDir: string
  force: boolean
}

export const mockOutputPlan = (argv: string[], defaultDir: string): MockOutputPlan => {
  const outIndex = argv.indexOf('--out')
  return {
    outDir: outIndex === -1 ? defaultDir : argv[outIndex + 1],
    force: argv.includes('--force'),
  }
}

/** 덮어쓰면 안 되면 이유를, 써도 되면 `null` */
export const refuseOverwrite = (plan: MockOutputPlan, hasMeta: boolean): string | null => {
  if (plan.force || hasMeta === false) return null
  return `${plan.outDir}/meta.json already exists (real batch data?). Use --out <dir> for another folder, or --force to overwrite.`
}
