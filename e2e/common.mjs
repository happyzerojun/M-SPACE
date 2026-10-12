// 화면 점검 공통: 주소, 결과 출력, 스크린샷 폴더, 로컬 DB SQL
import { execSync } from 'node:child_process'
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

export const DIR = dirname(fileURLToPath(import.meta.url))
/** 화면 주소. 개발 서버(5173)나 Docker 이미지(화면+API 한 주소)를 가리켜요 */
export const BASE = process.env.BASE ?? 'http://localhost:5173'
export const API = `${BASE}/api`
/** 스크린샷 (Git에 올리지 않아요) */
export const SHOTS = join(DIR, 'screenshots')
mkdirSync(SHOTS, { recursive: true })

/** 점검 결과 한 줄. run-all.mjs가 ✅/❌ 개수를 세요 */
export function check(ok, name, detail = '') {
  console.log(`${ok ? '✅' : '❌'} ${name}${detail ? ` — ${detail}` : ''}`)
  if (!ok) process.exitCode = 1
}

/** 로컬 Docker PostgreSQL에 SQL 실행 (시각 당기기 등 점검 준비용) */
export function sql(query) {
  return execSync(`docker exec -i ${process.env.E2E_DB_CONTAINER ?? 'mspace-postgres'} psql -U mspace -d mspace -tA`,
    { input: query }).toString().trim()
}
