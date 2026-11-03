package com.sparky.iptv.setup

import com.sparky.iptv.setup.SetupPage.esc
import com.sparky.iptv.setup.SetupPage.head
import com.sparky.iptv.setup.SetupPage.topBar

/** Phone pages for backup, restore and favorites folders, in the same style as the setup page. */
object PhonePages {

    private const val EXTRA = """
<style>
  .chk{display:flex;align-items:center;gap:12px;padding:14px 16px;border:1px solid #2A2C31;border-radius:12px;background:#16171A;margin:8px 0;font-size:15px}
  .chk input{width:22px;height:22px;margin:0;accent-color:#EEEBE5}
  .chk small{display:block;color:#8A8781;font-size:12px;margin-top:2px}
  .ok{margin-top:18px;padding:14px 16px;border-radius:12px;background:#17221A;border:1px solid #3B5A3E;color:#C9E3C6;font-size:14px;line-height:1.5}
  .warn{margin-top:18px;padding:14px 16px;border-radius:12px;background:#2A2417;border:1px solid #6B5A30;color:#F2E2B8;font-size:14px;line-height:1.5}
  .alt{width:100%;height:50px;margin-top:12px;border:1px solid #2A2C31;border-radius:14px;background:#16171A;color:#EEEBE5;font:inherit;font-size:15px;font-weight:600}
  input[type=file]{height:auto;padding:14px 16px}
  h2{font-size:13px;letter-spacing:.12em;text-transform:uppercase;color:#8A8781;margin:28px 0 10px}
  .card{border:1px solid #2A2C31;border-radius:14px;background:#16171A;padding:12px;margin:10px 0}
  .row{display:flex;align-items:center;gap:6px;padding:8px 4px;border-top:1px solid #222429;font-size:15px}
  .row:first-of-type{border-top:0}
  .row span{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  .row em{font-style:normal;color:#8A8781;font-size:12px;width:44px;flex:none}
  .ib{width:40px;height:36px;border:1px solid #2A2C31;border-radius:9px;background:#0E0F11;color:#EEEBE5;font:inherit;font-size:15px;flex:none}
  .ib:disabled{opacity:.3}
  .fh{display:flex;gap:6px;align-items:center}
  .fh input{height:44px;flex:1}
  .add{display:flex;gap:6px;margin-top:8px}
  .add select,.add input{height:44px;flex:1;min-width:0;border-radius:10px;border:1px solid #2A2C31;background:#0E0F11;color:#EEEBE5;font:inherit;font-size:15px;padding:0 10px}
  .empty{color:#8A8781;font-size:14px;padding:8px 4px}
  .bar{position:sticky;bottom:0;background:#0E0F11;padding:10px 0 16px}
</style>"""

    /** Back up settings: choose what to include, set a passphrase, download the file. */
    fun backup(device: String, token: String, error: String?, hasLogin: Boolean): String =
        head("Back up Sparky") + EXTRA + topBar(device) + """
<h1>Back up your settings</h1>
<p class="lead">Saves one file to your phone. Keep it in Files or iCloud; you can restore it on any TV, also after a fresh install.</p>
<form method="post" action="/make?k=${esc(token)}" onsubmit="return check()">
  <label class="chk"><input type="checkbox" name="login" id="login" ${if (hasLogin) "checked" else "disabled"} onchange="pw()"><span>Login<small>${if (hasLogin) "Server, username and password, encrypted" else "No login on this TV"}</small></span></label>
  <label class="chk"><input type="checkbox" name="favs" checked><span>Favorites and regions<small>Favorites, their order and folders, My regions</small></span></label>
  <label class="chk"><input type="checkbox" name="playback" checked><span>Playback settings<small>Subtitle style, sleep timer, audio and subtitle choices</small></span></label>
  <label class="chk"><input type="checkbox" name="history" checked><span>Watch history<small>Resume points and recently watched</small></span></label>
  <div id="pwf">
    <label for="p1">Passphrase</label>
    <input id="p1" name="pass" type="password" minlength="10" autocomplete="new-password" placeholder="At least 10 characters">
    <label for="p2">Repeat passphrase</label>
    <input id="p2" name="pass2" type="password" autocomplete="new-password">
    <p class="note" style="text-align:left">At least 10 characters; a short sentence is easy to remember. You'll need it to restore, and it can't be recovered.</p>
  </div>
  <div id="e" class="${if (error == null) "hide" else "err"}">${esc(error)}</div>
  <button class="send" type="submit">Download backup</button>
  <p class="note">The file goes only to this phone. Nothing leaves your home network.</p>
</form>
<script>
function pw(){document.getElementById('pwf').className=document.getElementById('login').checked?'':'hide';}
function err(t){var e=document.getElementById('e');e.className='err';e.textContent=t;return false;}
function check(){
  if(!document.getElementById('login').checked)return true;
  var a=document.getElementById('p1').value,b=document.getElementById('p2').value;
  if(a.length<10)return err('Use at least 10 characters for the passphrase. A short sentence works well.');
  if(a!==b)return err("The passphrases don't match.");
  return true;
}
pw();
</script>
</main></body></html>"""

    /** Restore from a backup file. [existing]: the TV already has a login (offer Add or Replace). */
    fun restore(device: String, token: String, existing: Boolean): String =
        head("Restore Sparky") + EXTRA + topBar(device) + """
<h1>Restore from a backup</h1>
<p class="lead">Pick the file you saved with Back up settings. Your TV gets your login, favorites and settings back.</p>
<label for="f">Backup file</label>
<input id="f" type="file" accept=".json,application/json,text/plain">
<label for="p">Passphrase</label>
<div class="pw"><input id="p" type="password" placeholder="Only needed if the backup has your login" autocomplete="off"><button type="button" onclick="var i=document.getElementById('p');i.type=i.type==='password'?'text':'password';this.textContent=i.type==='password'?'Show':'Hide'">Show</button></div>
${if (existing) """
<label>On this TV</label>
<label class="chk"><input type="radio" name="mode" value="add" checked><span>Add to what's here<small>Favorites and history are merged, settings replaced</small></span></label>
<label class="chk"><input type="radio" name="mode" value="replace"><span>Replace everything<small>The TV matches the backup exactly</small></span></label>
""" else ""}
<div id="m" class="hide"></div>
<button class="send" id="go" type="button" onclick="go(false)">Restore</button>
<button class="alt hide" id="again" type="button" onclick="go(true)">Restore anyway</button>
<script>
var K='${esc(token)}';
function show(t,c){var m=document.getElementById('m');m.className=c;m.textContent=t;}
function mode(){var r=document.querySelector('input[name=mode]:checked');return r?r.value:'replace';}
function go(confirmed){
  var f=document.getElementById('f').files[0];
  if(!f){show('Pick your backup file first.','err');return;}
  var b=document.getElementById('go');b.disabled=true;b.textContent='Restoring...';
  document.getElementById('again').className='alt hide';
  var r=new FileReader();
  r.onload=function(){
    fetch('/restore?k='+K,{method:'POST',headers:{'Content-Type':'application/json; charset=utf-8'},
      body:JSON.stringify({file:r.result,pass:document.getElementById('p').value,mode:mode(),confirm:confirmed})})
    .then(function(x){return x.json();})
    .then(function(j){
      b.disabled=false;b.textContent='Restore';
      if(j.ok){
        if(j.next){location.href=j.next;return;}
        show(j.message,'ok');b.className='hide';
      }else if(j.confirm){
        show(j.confirm,'warn');document.getElementById('again').className='alt';
      }else{
        show(j.error,'err');
        if(j.locked){b.className='hide';}
      }
    })
    .catch(function(){b.disabled=false;b.textContent='Restore';show("Couldn't reach the TV. Check you're on the same Wi-Fi and the TV screen is still open.",'err');});
  };
  r.onerror=function(){b.disabled=false;b.textContent='Restore';show("Couldn't read that file.",'err');};
  r.readAsText(f);
}
</script>
</main></body></html>"""

    /**
     * Favorites folders editor. [data] is JSON: {"channels":[{"key","name","num"}…] in your order,
     * "folders":[{"id","name","keys":[…]}…]}.
     */
    fun favorites(device: String, token: String, data: String): String =
        head("Favorite folders") + EXTRA + topBar(device) + """
<h1>Favorite folders</h1>
<p class="lead">Name your folders, put channels in them and set the order. Tap Save to send it to your TV.</p>
<h2>Folders</h2>
<div id="folders"></div>
<div class="add"><input id="nf" placeholder="New folder name" maxlength="30"><button class="ib" style="width:auto;padding:0 14px" type="button" onclick="newFolder()">Add</button></div>
<h2>All favorite channels</h2>
<div class="card" id="all"></div>
<div id="m" class="hide"></div>
<div class="bar"><button class="send" id="save" type="button" onclick="save()">Save to TV</button></div>
<script>
var K='${esc(token)}';
var D=${data.replace("</", "<\\/")};
var names={};D.channels.forEach(function(c){names[c.key]=c;});
var order=D.channels.map(function(c){return c.key;});
var folders=D.folders.map(function(f){return {id:f.id,name:f.name,keys:f.keys.filter(function(k){return names[k];})};});
var seq=0;
function el(t,c,x){var e=document.createElement(t);if(c)e.className=c;if(x!==undefined)e.textContent=x;return e;}
function btn(t,on,dis){var b=el('button','ib',t);b.type='button';b.onclick=on;b.disabled=!!dis;return b;}
function mv(a,i,d){var j=i+d;if(j<0||j>=a.length)return;var t=a[i];a[i]=a[j];a[j]=t;draw();}
function chRow(list,i,removable){
  var c=names[list[i]];var r=el('div','row');
  r.appendChild(el('em','',c.num));r.appendChild(el('span','',c.name));
  r.appendChild(btn('↑',function(){mv(list,i,-1);},i===0));
  r.appendChild(btn('↓',function(){mv(list,i,1);},i===list.length-1));
  if(removable)r.appendChild(btn('✕',function(){list.splice(i,1);draw();}));
  return r;
}
function draw(){
  var box=document.getElementById('folders');box.innerHTML='';
  if(!folders.length)box.appendChild(el('p','empty','No folders yet. Add one below, for example US News.'));
  folders.forEach(function(f,fi){
    var card=el('div','card');var h=el('div','fh');
    var inp=el('input');inp.value=f.name;inp.maxLength=30;inp.oninput=function(){f.name=inp.value;};
    h.appendChild(inp);
    h.appendChild(btn('↑',function(){mv(folders,fi,-1);},fi===0));
    h.appendChild(btn('↓',function(){mv(folders,fi,1);},fi===folders.length-1));
    h.appendChild(btn('✕',function(){if(confirm('Delete '+f.name+'? Its channels stay in Favorites.')){folders.splice(fi,1);draw();}}));
    card.appendChild(h);
    if(!f.keys.length)card.appendChild(el('p','empty','No channels yet.'));
    f.keys.forEach(function(k,i){card.appendChild(chRow(f.keys,i,true));});
    var add=el('div','add');var q=el('input');q.placeholder='Find a channel';q.type='search';
    var sel=el('select');
    function fill(){
      sel.innerHTML='';var o=el('option','','Add a channel…');o.value='';sel.appendChild(o);
      var t=q.value.toLowerCase();
      order.forEach(function(k){if(f.keys.indexOf(k)<0&&(!t||names[k].name.toLowerCase().indexOf(t)>=0||String(names[k].num).indexOf(t)===0)){var p=el('option','',names[k].num+'  '+names[k].name);p.value=k;sel.appendChild(p);}});
    }
    q.oninput=fill;sel.onchange=function(){if(sel.value){f.keys.push(sel.value);draw();}};
    fill();add.appendChild(q);add.appendChild(sel);card.appendChild(add);
    box.appendChild(card);
  });
  var all=document.getElementById('all');all.innerHTML='';
  if(!order.length)all.appendChild(el('p','empty','No favorite channels yet. Hold OK on a channel on the TV to add it.'));
  order.forEach(function(k,i){all.appendChild(chRow(order,i,false));});
}
function newFolder(){
  var n=document.getElementById('nf').value.trim();if(!n)return;
  seq++;folders.push({id:'new'+seq,name:n,keys:[]});document.getElementById('nf').value='';draw();
}
function show(t,c){var m=document.getElementById('m');m.className=c;m.textContent=t;}
function save(){
  var b=document.getElementById('save');b.disabled=true;b.textContent='Saving...';
  fetch('/favs?k='+K,{method:'POST',headers:{'Content-Type':'application/json; charset=utf-8'},body:JSON.stringify({order:order,folders:folders})})
  .then(function(x){return x.json();})
  .then(function(j){b.disabled=false;b.textContent='Save to TV';
    if(j.ok){show('Saved. Your TV is updated.','ok');if(j.folders){folders=j.folders;draw();}}else show(j.error,'err');})
  .catch(function(){b.disabled=false;b.textContent='Save to TV';show("Couldn't reach the TV. Check you're on the same Wi-Fi and the TV screen is still open.",'err');});
}
draw();
</script>
</main></body></html>"""
}
