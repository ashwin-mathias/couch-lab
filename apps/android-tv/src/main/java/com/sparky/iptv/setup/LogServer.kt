package com.sparky.iptv.setup

import com.sparky.iptv.data.AppLog
import com.sparky.iptv.data.SecretBox
import fi.iki.elonen.NanoHTTPD

/**
 * Serves the activity log to a phone on the same Wi-Fi while the Diagnostics page is open, on a
 * [PhoneServer] (8091–8099). The key works for 10 minutes after the last request.
 */
object LogServer {
    const val IDLE_MS = 10 * 60_000L

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun startOnFreePort(header: () -> String): PhoneServer? =
        PhoneServer.start(PhoneServer.LOG_PORTS, IDLE_MS, sliding = true) { html(page(header())) }

    private fun page(headerText: String): String {
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
button.alt{background:#16171A;color:#EEEBE5;border:1px solid #2A2C31;margin-top:-8px}
pre{white-space:pre-wrap;word-break:break-word;background:#16171A;border:1px solid #2A2C31;border-radius:12px;padding:14px;font-size:12px;line-height:1.5;color:#CFCBC3}
h2{font-size:13px;letter-spacing:.12em;text-transform:uppercase;color:#8A8781;margin:22px 0 8px}
.crash{border-color:#6B3A30;color:#F2C4B8}
</style></head><body><main>
<h1>Sparky IPTV diagnostics</h1>
<p>Tap the button, then paste into your chat with Claude. Passwords are never included.</p>
<button id="cp" onclick="copyAll(this)">Copy everything</button>
<button class="alt" onclick="selectAll()">Select all</button>
<script>
function selectAll(){var r=document.createRange();r.selectNodeContents(document.getElementById('all'));var s=window.getSelection();s.removeAllRanges();s.addRange(r);}
function copyAll(btn){
  var t=document.getElementById('all').innerText;
  function done(){btn.textContent='Copied';}
  function fallback(){
    // Plain http pages can't use the clipboard API; a hidden text box with the copy command works instead.
    var ta=document.createElement('textarea');ta.value=t;ta.setAttribute('readonly','');
    ta.style.position='fixed';ta.style.top='0';ta.style.left='0';ta.style.opacity='0';ta.style.fontSize='16px';
    document.body.appendChild(ta);ta.focus();ta.select();ta.setSelectionRange(0,t.length);
    var ok=false;try{ok=document.execCommand('copy');}catch(e){}
    document.body.removeChild(ta);
    if(ok){done();}else{selectAll();btn.textContent='Text selected: tap Copy';}
  }
  if(navigator.clipboard&&window.isSecureContext){navigator.clipboard.writeText(t).then(done,fallback);}else{fallback();}
}
</script>
<div id="all">
<pre>${esc(headerText)}</pre>
${if (crash != null) "<h2>Last crash</h2><pre class=\"crash\">${esc(crash)}</pre>" else ""}
<h2>Activity log</h2>
<pre>${esc(AppLog.text())}</pre>
</div>
</main></body></html>"""
        return body
    }
}
