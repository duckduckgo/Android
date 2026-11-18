#!/usr/bin/env node
// Minimal Chrome DevTools Protocol client for the app's WebViews (Node 22+ for the built-in WebSocket).
// usage: cdp.mjs <port> targets
//        cdp.mjs <port> eval "<js expression>" [url-regex]
// eval picks the visible page target (or the first whose URL matches url-regex), awaits promises,
// and prints the JSON-serialised result. Exit code 1 on a JS exception or missing target.

const [port, command, arg, urlPattern] = process.argv.slice(2);
if (!port || !command) {
  console.error('usage: cdp.mjs <port> targets | eval "<js>" [url-regex]');
  process.exit(2);
}

const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
const pages = targets.filter((t) => t.type === 'page');

if (command === 'targets') {
  for (const t of pages) {
    const d = safeParse(t.description);
    console.log(`${d.visible ? 'visible' : 'hidden '}  ${d.attached ? 'attached' : 'detached'}  ${t.url}`);
  }
  process.exit(0);
}

if (command !== 'eval') {
  console.error(`unknown command ${command}`);
  process.exit(2);
}

const target = urlPattern
  ? pages.find((t) => new RegExp(urlPattern).test(t.url))
  : pages.find((t) => { const d = safeParse(t.description); return d.visible && d.attached; })
    ?? pages.find((t) => safeParse(t.description).attached)
    ?? pages[0];
if (!target) {
  console.error(`no WebView page target${urlPattern ? ` matching ${urlPattern}` : ''}`);
  process.exit(1);
}

const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => {
  ws.onopen = resolve;
  ws.onerror = () => reject(new Error(`cannot connect to ${target.url}`));
});

const reply = await new Promise((resolve) => {
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id === 1) resolve(msg);
  };
  ws.send(JSON.stringify({
    id: 1,
    method: 'Runtime.evaluate',
    params: { expression: arg, returnByValue: true, awaitPromise: true, userGesture: false },
  }));
});
ws.close();

if (reply.error || reply.result.exceptionDetails) {
  console.error(JSON.stringify(reply.error ?? reply.result.exceptionDetails.exception?.description ?? reply.result.exceptionDetails));
  process.exit(1);
}
console.log(JSON.stringify(reply.result.result.value ?? null));

function safeParse(s) {
  try { return JSON.parse(s || '{}'); } catch { return {}; }
}
