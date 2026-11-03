// pages.yml 단계: 바뀐 파일 목록(줄마다 하나)을 표준 입력으로 받아 data_only=true|false 를 GITHUB_OUTPUT 에 쓴다
import { appendFileSync, readFileSync } from 'node:fs'
import { isDataOnlyChange } from './data-only.ts'

const files = readFileSync(0, 'utf8').split('\n')
const result = `data_only=${isDataOnlyChange(files)}`
console.log(`${files.filter((file) => file.trim() !== '').length} changed files → ${result}`)
const output = process.env.GITHUB_OUTPUT
if (output !== undefined) appendFileSync(output, `${result}\n`)
