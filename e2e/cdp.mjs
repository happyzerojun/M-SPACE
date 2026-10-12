// 설치 없이 Chrome DevTools Protocol로 브라우저를 조종하는 작은 도우미 (화면 점검용, Playwright 같은 추가 설치가 필요 없어요)
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const DEFAULT_CHROME = {
  darwin: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  win32: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  linux: 'google-chrome',
}
const CHROME = process.env.CHROME_PATH ?? DEFAULT_CHROME[process.platform] ?? 'google-chrome'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

/** 실행마다 빈 프로필로 시작해요 (예전 실행의 로그인 토큰이 남아서 결과가 바뀌는 일을 막아요) */
export async function launch({ port = 9334 } = {}) {
  const profile = mkdtempSync(join(tmpdir(), 'mspace-e2e-'))
  const proc = spawn(CHROME, [
    '--headless=new', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`,
    '--no-first-run', '--no-default-browser-check', '--window-size=1280,900', 'about:blank',
  ], { stdio: 'ignore' })

  let target
  for (let i = 0; i < 50 && !target; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()
      target = list.find((t) => t.type === 'page')
    } catch { /* 아직 안 떴어요 */ }
    if (!target) await sleep(200)
  }
  if (!target) throw new Error('Chrome이 시작되지 않았어요')

  const ws = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((r) => ws.addEventListener('open', r, { once: true }))
  let id = 0
  const pending = new Map()
  ws.addEventListener('message', (e) => {
    const msg = JSON.parse(e.data)
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id)
      pending.delete(msg.id)
      msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result)
    }
  })
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const msgId = ++id
    pending.set(msgId, { resolve, reject })
    ws.send(JSON.stringify({ id: msgId, method, params }))
  })
  await send('Page.enable')
  await send('Runtime.enable')

  const page = {
    send,
    async eval(expression) {
      const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
      if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description ?? 'eval error')
      return r.result.value
    },
    async goto(url) {
      await send('Page.navigate', { url })
      await page.waitFor('document.readyState === "complete"')
      await sleep(300)
    },
    async waitFor(expression, timeout = 5000) {
      const end = Date.now() + timeout
      while (Date.now() < end) {
        try { if (await page.eval(`Boolean(${expression})`)) return } catch { /* 페이지 이동 중 */ }
        await sleep(100)
      }
      throw new Error(`시간 초과: ${expression}`)
    },
    /** React 입력칸은 value만 바꾸면 안 되고 input 이벤트를 보내야 상태가 바뀌어요 */
    async type(selector, value) {
      await page.eval(`(() => {
        const el = document.querySelector(${JSON.stringify(selector)})
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set
        setter.call(el, ${JSON.stringify(value)})
        el.dispatchEvent(new Event('input', { bubbles: true }))
      })()`)
    },
    async click(selector) {
      await page.eval(`document.querySelector(${JSON.stringify(selector)}).click()`)
    },
    async clickText(text) {
      await page.eval(`[...document.querySelectorAll('a,button')].find(e => e.textContent.trim() === ${JSON.stringify(text)}).click()`)
    },
    async clickButton(text) {
      await page.eval(`[...document.querySelectorAll('button')].find(e => e.textContent.trim() === ${JSON.stringify(text)}).click()`)
    },
    /** 파일 선택 창 없이 <input type=file>에 파일을 넣어요 (change 이벤트가 발생해요) */
    async uploadFile(selector, filePath) {
      const { root } = await send('DOM.getDocument')
      const { nodeId } = await send('DOM.querySelector', { nodeId: root.nodeId, selector })
      await send('DOM.setFileInputFiles', { files: [filePath], nodeId })
    },
    async typeArea(selector, value) {
      await page.eval(`(() => {
        const el = document.querySelector(${JSON.stringify(selector)})
        const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set
        setter.call(el, ${JSON.stringify(value)})
        el.dispatchEvent(new Event('input', { bubbles: true }))
      })()`)
    },
    text: () => page.eval('document.body.innerText'),
    path: () => page.eval('location.pathname + location.search'),
    async viewport(width, height) {
      await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 600 })
    },
    async screenshot(file) {
      const { data } = await send('Page.captureScreenshot', { format: 'png' })
      writeFileSync(file, Buffer.from(data, 'base64'))
    },
    sleep,
    close() {
      ws.close()
      proc.kill()
      setTimeout(() => rmSync(profile, { recursive: true, force: true }), 500)
    },
  }
  return page
}
