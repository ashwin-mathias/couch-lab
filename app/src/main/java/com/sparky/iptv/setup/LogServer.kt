package com.sparky.iptv.setup

import com.sparky.iptv.data.AppLog
import fi.iki.elonen.NanoHTTPD

/** Serves the activity log to a phone on the same Wi-Fi while the Diagnostics page is open. */
class LogServer(port: Int, private val header: () -> String) : NanoHTTPD(port) {

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    override fun serve(session: IHTTPSession): Response {
        val crash = AppLog.lastCrash()
        val body = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Sparky IPTV diagnostics</title>
<style>
body{margin:0;background:#0E0F11;color:#EEEBE5;font-family:-apple-system,BlinkMacSystemFont,Roboto,sans-serif}
main{max-width:760px;margin:0 auto;padding:24px 16px 40px}
h1{font-size:22px;margin:0 0 6px}
p{color:#A19D96;font-size:14px;margin:0 0 16px;line-height:1.5}
button{width:100%;height:50px;border:0;border-radius:12px;background:#EEEBE5;color:#0E0F11;font:inherit;font-weight:700;font-size:16px;margin:8px 0 18px}
pre{white-space:pre-wrap;word-break:break-word;background:#16171A;border:1px solid #2A2C31;border-radius:12px;padding:14px;font-size:12px;line-height:1.5;color:#CFCBC3}
h2{font-size:13px;letter-spacing:.12em;text-transform:uppercase;color:#8A8781;margin:22px 0 8px}
.crash{border-color:#6B3A30;color:#F2C4B8}
</style></head><body><main>
<h1>Sparky IPTV diagnostics</h1>
<p>Tap the button, then paste into your chat with Claude. Passwords are never included.</p>
<button onclick="navigator.clipboard.writeText(document.getElementById('all').innerText).then(function(){this.textContent='Copied'}.bind(this))">Copy everything</button>
<div id="all">
<pre>${esc(header())}</pre>
${if (crash != null) "<h2>Last crash</h2><pre class=\"crash\">${esc(crash)}</pre>" else ""}
<h2>Activity log</h2>
<pre>${esc(AppLog.text())}</pre>
</div>
</main></body></html>"""
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)
    }

    companion object {
        fun startOnFreePort(header: () -> String): LogServer? {
            for (port in 8091..8099) {
                val s = LogServer(port, header)
                try {
                    s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    return s
                } catch (_: Exception) {
                }
            }
            return null
        }
    }
}
