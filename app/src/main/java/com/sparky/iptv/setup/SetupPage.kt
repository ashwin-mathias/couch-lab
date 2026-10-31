package com.sparky.iptv.setup

import com.sparky.iptv.data.Playlist

/** The phone-side web page, styled to match the TV app. */
object SetupPage {

    internal fun esc(s: String?): String = (s ?: "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    internal const val STYLE = """
<style>
  *{box-sizing:border-box}
  body{margin:0;background:#0E0F11;color:#EEEBE5;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Inter,sans-serif;-webkit-font-smoothing:antialiased}
  main{max-width:440px;margin:0 auto;padding:32px 22px 40px}
  .top{display:flex;align-items:center;gap:10px;font-weight:600;font-size:16px}
  .dev{margin-left:auto;font-size:13px;color:#A19D96;font-weight:500;display:flex;align-items:center;gap:7px}
  .dot{width:7px;height:7px;border-radius:50%;background:#9DB89A;display:inline-block}
  h1{font-size:28px;line-height:1.2;margin:28px 0 8px}
  p.lead{margin:0 0 24px;color:#A19D96;font-size:15px;line-height:1.55}
  .tabs{display:flex;gap:6px;background:#16171A;border:1px solid #2A2C31;border-radius:12px;padding:4px;margin-bottom:20px}
  .tabs button{flex:1;height:40px;border:0;border-radius:9px;background:transparent;color:#A19D96;font:inherit;font-size:14px;font-weight:600}
  .tabs button.on{background:#EEEBE5;color:#0E0F11}
  label{display:block;font-size:13px;font-weight:600;color:#CFCBC3;margin:16px 0 8px}
  input{width:100%;height:50px;padding:0 16px;border-radius:12px;border:1px solid #2A2C31;background:#16171A;color:#EEEBE5;font:inherit;font-size:16px}
  input:focus{outline:none;border:1.5px solid #EEEBE5}
  input::placeholder{color:#8A8781}
  .pw{position:relative}
  .pw input{padding-right:64px}
  .pw button{position:absolute;right:4px;top:4px;height:42px;padding:0 12px;border:0;background:transparent;color:#A19D96;font:inherit;font-size:13px;font-weight:600}
  .send{width:100%;height:54px;margin-top:28px;border:0;border-radius:14px;background:#EEEBE5;color:#0E0F11;font:inherit;font-size:16px;font-weight:700}
  .send:disabled{opacity:.6}
  .err{margin-top:18px;padding:14px 16px;border-radius:12px;background:#2A1A17;border:1px solid #6B3A30;color:#F2C4B8;font-size:14px;line-height:1.5}
  .note{margin-top:16px;text-align:center;font-size:13px;color:#8A8781;line-height:1.5}
  .hide{display:none}
  .done{text-align:center;padding-top:60px}
  .tick{width:64px;height:64px;border-radius:50%;border:2px solid #9DB89A;margin:0 auto 20px;display:flex;align-items:center;justify-content:center;color:#9DB89A;font-size:30px}
</style>"""

    internal fun head(title: String) = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>$title</title>$STYLE</head><body><main>"""

    internal fun topBar(device: String) =
        """<div class="top">Sparky IPTV<span class="dev"><span class="dot"></span>${esc(device)}</span></div>"""

    fun form(device: String, token: String, prev: Playlist?, error: String?, note: String? = null): String {
        val isM3u = prev?.type == "m3u"
        return head("Connect your TV") + topBar(device) + """
<h1>Connect your TV</h1>
<p class="lead">Enter the login from your provider. It goes straight to your TV over your home Wi-Fi.</p>
${if (note != null) "<div class=\"err\" style=\"background:#17221A;border-color:#3B5A3E;color:#C9E3C6;margin:0 0 20px\">${esc(note)}</div>" else ""}
<div class="tabs">
  <button type="button" id="tx" class="${if (isM3u) "" else "on"}" onclick="pick('xtream')">Login</button>
  <button type="button" id="tm" class="${if (isM3u) "on" else ""}" onclick="pick('m3u')">M3U link</button>
</div>
<form method="post" action="/save?k=${esc(token)}" onsubmit="document.getElementById('go').disabled=true;document.getElementById('go').textContent='Checking login...'">
  <input type="hidden" name="type" id="type" value="${if (isM3u) "m3u" else "xtream"}">
  <label for="name">Playlist name</label>
  <input id="name" name="name" value="${esc(prev?.name ?: "Home")}" autocomplete="off">
  <div id="xf" class="${if (isM3u) "hide" else ""}">
    <label for="url">Server URL</label>
    <input id="url" name="url" type="url" inputmode="url" placeholder="http://example.com:8080" value="${esc(if (isM3u) "" else prev?.url)}" autocapitalize="off" autocorrect="off" spellcheck="false">
    <label for="user">Username</label>
    <input id="user" name="user" placeholder="Username" value="${esc(prev?.user)}" autocapitalize="off" autocorrect="off" spellcheck="false">
    <label for="pass">Password</label>
    <div class="pw"><input id="pass" name="pass" type="password" placeholder="Password" value="${esc(prev?.pass)}" autocapitalize="off" autocorrect="off" spellcheck="false"><button type="button" onclick="var i=document.getElementById('pass');i.type=i.type==='password'?'text':'password';this.textContent=i.type==='password'?'Show':'Hide'">Show</button></div>
  </div>
  <div id="mf" class="${if (isM3u) "" else "hide"}">
    <label for="m3u">Playlist link (M3U)</label>
    <input id="m3u" name="m3u" type="url" inputmode="url" placeholder="http://example.com/get.php?..." value="${esc(if (isM3u) prev?.url else "")}" autocapitalize="off" autocorrect="off" spellcheck="false">
  </div>
  ${if (error != null) "<div class=\"err\">${esc(error)}</div>" else ""}
  <button class="send" id="go" type="submit">Send to TV</button>
  <p class="note">Your login is only sent to your TV on this Wi-Fi network.</p>
</form>
<p class="note" style="margin-top:28px"><a href="/restore?k=${esc(token)}" style="color:#EEEBE5;font-weight:600">Restore from a backup file</a></p>
<script>
function pick(t){document.getElementById('type').value=t;
document.getElementById('xf').className=t==='m3u'?'hide':'';
document.getElementById('mf').className=t==='m3u'?'':'hide';
document.getElementById('tx').className=t==='m3u'?'':'on';
document.getElementById('tm').className=t==='m3u'?'on':'';}
</script>
</main></body></html>"""
    }

    fun done(device: String): String = head("Connected") + topBar(device) + """
<div class="done">
  <div class="tick">&#10003;</div>
  <h1>You're connected</h1>
  <p class="lead">Your TV is loading your channels now. You can close this page.</p>
</div>
</main></body></html>"""
}
