// 화면 점검 전체 실행: node e2e/run-all.mjs [이름...]   (BASE로 대상 주소를 바꿀 수 있어요)
// 점검마다 ✅/❌를 보여주고, 하나라도 실패하면 종료 코드 1이에요
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'
import { BASE, DIR } from './common.mjs'

const CHECKS = ['layout', 'auth']
const only = process.argv.slice(2)
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

// 같은 PC의 다른 프로젝트 화면을 점검하지 않게, 대상이 M-SPACE인지 먼저 확인해요 (TS-10)
const title = await fetch(BASE).then((r) => r.text()).then((h) => h.match(/<title>([^<]*)/)?.[1]).catch((e) => `연결 실패: ${e.message}`)
if (!title?.includes('M-SPACE')) {
  console.log(`❌ ${BASE}는 M-SPACE 화면이 아니에요 (title: ${title}). BASE를 확인해 주세요.`)
  process.exit(1)
}

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
