// 화면 점검 전체 실행: node e2e/run-all.mjs [이름...]   (BASE로 대상 주소를 바꿀 수 있어요)
// 점검마다 ✅/❌를 보여주고, 하나라도 실패하면 종료 코드 1이에요
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'
import { DIR } from './common.mjs'

const CHECKS = ['layout']
const only = process.argv.slice(2)
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

let passed = 0, failed = 0
for (const name of CHECKS.filter((c) => !only.length || only.includes(c))) {
  const res = spawnSync(process.execPath, [join(DIR, `check-${name}.mjs`)], { encoding: 'utf8' })
  const out = (res.stdout ?? '') + (res.stderr ?? '')
  const ok = (out.match(/✅/g) ?? []).length
  const bad = (out.match(/❌/g) ?? []).length + (res.status !== 0 && !out.includes('❌') ? 1 : 0)
  passed += ok
  failed += bad
  console.log(`\n▶ ${name}: ✅ ${ok}  ❌ ${bad}`)
  console.log(out.trim().split('\n').map((l) => `  ${l}`).join('\n'))
  await sleep(1000)   // 앞 점검의 Chrome이 완전히 꺼질 때까지
}
console.log(`\n합계: ✅ ${passed}  ❌ ${failed}`)
process.exit(failed ? 1 : 0)
