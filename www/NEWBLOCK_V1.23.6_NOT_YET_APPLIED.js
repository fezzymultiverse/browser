/* ======================================================================================================================
   V1.23.6 additions: Downloads panel, Security Console in Settings, collection colours, JSON import, Conduint status.
   ====================================================================================================================== */

/* ---------- Downloads panel. The engine is native (FBDownloader.java): own progress, pause, resume, saved into Downloads/Festers. ---------- */
const DL={jobs:[],tab:'dl',hq:''};
const dlLive=d=>d.state==='downloading'||d.state==='queued'||d.state==='saving';
const dlPct=d=>d.state==='completed'?100:(d.total>0?Math.max(0,Math.min(100,Number(d.progress)||0)):0);
function dlEta(d){if(d.state!=='downloading'||!(d.total>0)||!(d.speed>0))return '';const s=Math.max(1,Math.round((d.total-d.bytes)/d.speed));return s<60?s+' s left':s<3600?Math.round(s/60)+' min left':(s/3600).toFixed(1)+' h left'}
function dlLine(d){
 const st=d.state,sz=fSize(d.total>0?d.total:(d.bytes||0));
 if(st==='completed')return 'Saved in '+(d.path||'Downloads/Festers')+' · '+sz;
 if(st==='failed')return 'Failed: '+(d.error||d.desc||'unknown error');
 if(st==='paused')return 'Paused'+(d.total>0?' at '+dlPct(d)+'%':'')+' · '+fSize(d.bytes||0)+(d.total>0?' of '+fSize(d.total):'')+(d.error&&d.error!=='Paused'?' · '+d.error:'');
 if(st==='queued')return 'Waiting to start…';
 if(st==='saving')return 'Saving into Downloads…';
 return (d.total>0?dlPct(d)+'% · ':'')+fSize(d.bytes||0)+(d.total>0?' of '+fSize(d.total):'')+(d.speed>0?' · '+fSize(d.speed)+'/s':'')+(dlEta(d)?' · '+dlEta(d):'');
}
function dlBtns(d){
 const b=(a,l,c)=>`<button class="btn sm ${c||''}" data-dla="${a}" data-id="${d.id}">${l}</button>`,st=d.state;
 if(st==='downloading'||st==='queued')return b('pause','Pause')+b('cancel','Cancel','bad');
 if(st==='saving')return '';
 if(st==='paused')return b('resume','Resume','pri')+b('cancel','Cancel','bad');
 if(st==='failed')return b('resume','Retry','pri')+b('rm','Remove');
 return b('open','Open','pri')+b('copy','Copy link')+b('del','Delete file','bad')+b('rm','Remove');
}
function dlRow(d){
 const p=dlPct(d),ind=dlLive(d)&&!(d.total>0)&&d.state!=='queued';
 const big=d.state==='completed'?'Done':(d.total>0&&d.state!=='failed'?p+'%':'');
 return `<div class="dlr ${esc(d.state)}" data-dj="${d.id}"><div class="dlh"><div class="dlt"><b>${esc(d.name||'Download')}</b><small data-dls>${esc(dlLine(d))}</small></div><span class="dlp" data-dlp>${big}</span></div><div class="dlbar ${ind?'ind':''}"><i data-dlb style="width:${ind?35:p}%"></i></div><div class="acts dla">${dlBtns(d)}</div></div>`;
}
function dlBadge(){try{const n=DL.jobs.filter(dlLive).length,b=$('#rail .rb[data-p="dl"]');if(b){if(n)b.setAttribute('data-n',n>9?'9+':String(n));else b.removeAttribute('data-n')}}catch(e){}}
function dlConfig(){try{if(NAT)FBW().dlConfig({wifiOnly:!!S.dlWifi})}catch(e){}}
async function dlRefresh(){
 if(!NAT)return false;
 try{const r=await FBW().downloadJobs(),a=(r&&r.jobs)||[];const before=JSON.stringify(DL.jobs.map(x=>[x.id,x.state,x.bytes]));DL.jobs=a;dlBadge();return before!==JSON.stringify(a.map(x=>[x.id,x.state,x.bytes]))}catch(e){return false}
}
async function dlInit(){
 dlConfig();await dlRefresh();
 try{FBW().dlResumeNet()}catch(e){} /* downloads that were running when the app closed, or that lost the connection, continue now */
 addEventListener('online',()=>{try{FBW().dlResumeNet()}catch(e){}});
}
function dlOnEvent(e){
 if(!e||e.id==null)return;
 const i=DL.jobs.findIndex(x=>x.id===e.id);
 if(e.state==='removed'||e.status==='removed'){if(i>=0)DL.jobs.splice(i,1);dlBadge();if(panel==='dl'&&DL.tab==='dl')renderDl();return}
 const prev=i>=0?DL.jobs[i]:null;
 if(i>=0)DL.jobs[i]=e;else DL.jobs.unshift(e);
 dlBadge();
 if(panel!=='dl'||DL.tab!=='dl')return;
 const row=$(`[data-dj="${e.id}"]`);
 if(row&&prev&&prev.state===e.state){ /* same state: update the numbers in place, no flicker */
  const s=$('[data-dls]',row),p=$('[data-dlp]',row),bar=$('[data-dlb]',row),nm=$('.dlt b',row),ind=dlLive(e)&&!(e.total>0)&&e.state!=='queued';
  if(s)s.textContent=dlLine(e);
  if(nm&&e.name&&nm.textContent!==e.name)nm.textContent=e.name;
  if(p)p.textContent=e.state==='completed'?'Done':(e.total>0&&e.state!=='failed'?dlPct(e)+'%':'');
  if(bar){bar.style.width=(ind?35:dlPct(e))+'%';bar.parentNode.classList.toggle('ind',ind)}
  return;
 }
 renderDl();
}
function renderDl(){
 const el=$('#dbody');if(!el)return;
 const act=DL.jobs.filter(dlLive).length;
 const tabs=`<div class="dtabs"><button data-dt="dl" class="${DL.tab==='dl'?'on':''}">Downloads${act?' ('+act+')':''}</button><button data-dt="hist" class="${DL.tab==='hist'?'on':''}">History</button></div>`;
 let body='';
 if(DL.tab==='hist'){
  const q=DL.hq.toLowerCase(),list=H.filter(h=>!q||(h.url+' '+h.title).toLowerCase().includes(q)).slice(0,200);
  let day='',html='';
  list.forEach(h=>{const d=new Date(h.t).toDateString();if(d!==day){day=d;html+=`<div class="mh" style="padding:12px 6px 4px">${esc(d)}</div>`}
   html+=`<div class="li"><div data-hg="${esc(h.url)}" style="flex:1;min-width:0"><b>${esc(h.title)}</b><small>${esc(h.url)}</small></div><small class="mu" style="flex:none">${new Date(h.t).toTimeString().slice(0,5)}</small></div>`});
  body=`<div class="row2" style="gap:8px;margin-bottom:8px"><input class="in" id="dlhq" placeholder="Search history" value="${esc(DL.hq)}" autocapitalize="off"><button class="btn bad sm" id="dlhclr">Clear</button></div><div id="dlhl">${html||'<p class="mu" style="text-align:center;padding:24px">Nothing here yet.</p>'}</div><div class="acts" style="margin-top:10px"><button class="btn sm" id="dlhfull">Open full history page</button></div>`;
 }else if(!NAT){
  body='<p class="mu">Downloads need the installed Android app. In this preview, files are saved by the browser.</p>';
 }else{
  body=`<div class="row2" style="gap:8px"><input class="in" id="dlu" placeholder="Paste a link to download" autocapitalize="off" inputmode="url"><button class="btn pri sm" id="dlgo">Download</button></div>
  <div class="acts" style="justify-content:flex-start;flex-wrap:wrap;margin:8px 0"><button class="btn sm" id="dlall">Resume all</button><button class="btn sm" id="dlclr">Clear finished</button><button class="btn sm" id="dlfiles">Files</button><button class="btn sm" id="dlset">Settings</button></div>
  <p class="mu" style="margin:0 0 8px"><small>${act?act+' active · ':''}${DL.jobs.length} ${DL.jobs.length===1?'download':'downloads'}. Files are saved in Downloads/Festers on your phone.</small></p>
  <div id="dll">${DL.jobs.length?DL.jobs.map(dlRow).join(''):'<p class="mu" style="text-align:center;padding:24px">No downloads yet. Tap a download link on any page, or paste one above.</p>'}</div>`;
 }
 el.innerHTML=tabs+body;
 $$('[data-dt]',el).forEach(b=>b.onclick=()=>{DL.tab=b.dataset.dt;renderDl();if(DL.tab==='dl')dlRefresh().then(ch=>{if(ch&&panel==='dl'&&DL.tab==='dl')renderDl()})});
 if(DL.tab==='hist'){
  const f=$('#dlhq',el);f.oninput=()=>{DL.hq=f.value;const p=f.selectionStart;renderDl();const g=$('#dlhq');if(g){g.focus();g.setSelectionRange(p,p)}};
  $('#dlhclr',el).onclick=async()=>{if(await confirmBox('Delete all browsing history?','Delete')){H=[];LS.set('history',H);renderDl()}};
  $('#dlhfull',el).onclick=()=>{closePanel();openInternal('about:history')};
  $('#dlhl',el).onclick=e=>{const g=e.target.closest('[data-hg]');if(g){const u=g.dataset.hg;closePanel();nav(curTab(),u,{user:true})}};
  return;
 }
 if(!NAT)return;
 const F=FBW();
 $('#dlgo',el).onclick=async()=>{
  let u=($('#dlu',el).value||'').trim();if(!u)return toast('Paste a link first');
  if(!/^[a-z][a-z0-9+.-]*:/i.test(u))u='https://'+u;
  if(!/^https?:\/\//i.test(u))return toast('Only http and https links can be downloaded');
  try{const r=await F.dlStart({id:cur,url:u,referer:'',wifiOnly:!!S.dlWifi});if(!r||!r.ok)toast((r&&r.desc)||'Download could not start');else{$('#dlu',el).value='';dlNotifOnce()}}catch(err){toast('Download could not start')}
 };
 $('#dlall',el).onclick=async()=>{let n=0;for(const d of DL.jobs.filter(x=>x.state==='paused'||x.state==='failed')){try{const r=await F.dlResume({id:d.id});if(r&&r.ok)n++}catch(err){}}toast(n?'Resumed '+n:'Nothing to resume')};
 $('#dlclr',el).onclick=async()=>{try{const r=await F.dlClear();await dlRefresh();renderDl();toast((r&&r.n)||0?'Cleared '+r.n:'Nothing to clear')}catch(err){}};
 $('#dlfiles',el).onclick=()=>{closePanel();dlManager()};
 $('#dlset',el).onclick=()=>{closePanel();openInternal('about:preferences#downloads')};
 $('#dll',el).onclick=async e=>{
  const b=e.target.closest('[data-dla]');if(!b)return;
  const id=+b.dataset.id,a=b.dataset.dla,d=DL.jobs.find(x=>x.id===id);
  try{
   if(a==='pause')await F.dlPause({id});
   else if(a==='resume')await F.dlResume({id});
   else if(a==='cancel'){if(await confirmBox('Cancel this download? The part already saved is thrown away.','Cancel download'))await F.dlCancel({id,deleteFile:false})}
   else if(a==='rm')await F.dlCancel({id,deleteFile:false});
   else if(a==='del'){if(await confirmBox('Delete the downloaded file from your phone?','Delete'))await F.dlCancel({id,deleteFile:true})}
   else if(a==='open'){const r=await F.openDownload({id});if(!r||!r.ok)toast((r&&r.desc)||'Could not open the file')}
   else if(a==='copy'&&d){copyText(d.url);toast('Link copied')}
  }catch(err){toast('Download action failed: '+(err&&err.message||err))}
 };
 dlRefresh().then(ch=>{if(ch&&panel==='dl'&&DL.tab==='dl'&&!document.activeElement.closest?.('#dlu'))renderDl()});
}
if(NAT)setTimeout(dlInit,1200);

/* ---------- Security Console: every security tool now lives inside Settings ---------- */
function renderConsole(t){
 const el=t&&t.el?$('#scon',t.el):null;if(!el)return;
 el.innerHTML=`<div class="stabs">${ST.map(s=>`<button class="${s[0]===secTab?'on':''}" data-st="${s[0]}">${ic(s[2],15)}<span>${s[1]}</span></button>`).join('')}</div><div id="sb"></div>`;
 $('.stabs',el).onclick=e=>{const b=e.target.closest('[data-st]');if(b){secTab=b.dataset.st;renderConsole(t)}};
 try{SECV[secTab]($('#sb',el))}catch(err){$('#sb',el).innerHTML='<p class="mu">This tool could not open: '+esc(String(err&&err.message||err))+'</p>'}
}
function openConsole(tab,t){
 if(tab&&SECV[tab])secTab=tab;
 const x=t||curTab();
 if(x&&/^about:preferences/.test(x.hist[x.pos]||'')){x.prefSec='console';x.prefOpen=true;renderPrefs(x,'console');try{x.el.scrollTop=0}catch(e){}if(x.id!==cur)switchTab(x.id)}
 else openInternal('about:preferences#console');
}

/* ---------- Collection colour tags ---------- */
const COLS=[['Red','#ef4444'],['Orange','#f97316'],['Yellow','#eab308'],['Green','#22c55e'],['Teal','#14b8a6'],['Blue','#3b82f6'],['Indigo','#6366f1'],['Purple','#a855f7'],['Pink','#ec4899'],['Grey','#94a3b8']];
const colName=h=>{const x=COLS.find(c=>c[1]===h);return x?x[0]:'Colour'};
function pickColour(cur,title){
 return new Promise(res=>{
  const sw=COLS.map(c=>`<button class="swc ${cur===c[1]?'on':''}" data-pc="${c[1]}" style="background:${c[1]};width:38px;height:38px" aria-label="${c[0]}" title="${c[0]}"></button>`).join('');
  modal(title||'Colour tag',`<div class="sws" style="flex-wrap:wrap;gap:10px;justify-content:flex-start">${sw}</div><p class="mu" style="margin:12px 0 0"><small>Tag colours make a collection easy to spot, and you can filter the list by colour.</small></p>`,
   [{label:'No colour',fn:()=>res('')},{label:'Cancel',fn:()=>res(null)}],
   {cancel:()=>res(null),open:(m,close)=>{m.addEventListener('click',e=>{const b=e.target.closest('[data-pc]');if(!b)return;res(b.dataset.pc);close()})}});
 });
}
const colDot=(c,s)=>`<span class="cdot" style="background:${c||'transparent'};${c?'':'border:1.5px dashed var(--mu);'}width:${s||12}px;height:${s||12}px"></span>`;

/* ---------- JSON import from password managers ---------- */
function jsonLogins(d){
 const out=[],st={skipped:0,enc:false,fmt:''};
 const s=v=>v==null?'':(typeof v==='object'?'':String(v)).trim();
 const add=o=>{
  o.title=s(o.title);o.url=s(o.url);o.user=s(o.user);o.pass=s(o.pass);o.totp=s(o.totp);o.notes=s(o.notes);o.folder=s(o.folder);
  if(o.note){if(!o.title&&!o.notes){st.skipped++;return}out.push(o);return}
  if(!o.user&&!o.pass){st.skipped++;return}
  out.push(o);
 };
 const firstUrl=u=>{if(Array.isArray(u)){const f=u[0];return f?(typeof f==='string'?f:(f.uri||f.url||'')):''}return u||''};
 const lc=o=>{const m={};Object.keys(o||{}).forEach(k=>{m[k.toLowerCase().replace(/[\s_-]/g,'')]=o[k]});return m};
 if(d&&d.encrypted===true){st.enc=true;return {out,st}}
 /* Bitwarden (and Vaultwarden): {folders:[],items:[{type,name,login:{uris,username,password,totp},notes,fields}]} */
 if(d&&Array.isArray(d.items)&&d.items.length&&d.items.some(i=>i&&(i.login||i.secureNote||i.card||i.identity))){
  st.fmt='Bitwarden';const fo={};(d.folders||[]).forEach(f=>{if(f&&f.id)fo[f.id]=f.name});
  d.items.forEach(i=>{
   if(!i)return;
   const cf=(i.fields||[]).map(f=>s(f.name)+': '+s(f.value)).join('\n');
   if(i.login)add({title:i.name,url:firstUrl(i.login.uris),user:i.login.username,pass:i.login.password,totp:i.login.totp,notes:i.notes,folder:fo[i.folderId]||'',fav:i.favorite?'1':'',cf});
   else if(i.secureNote||i.type===2)add({note:1,title:i.name,notes:i.notes,folder:fo[i.folderId]||''});
   else st.skipped++;
  });
  return {out,st};
 }
 /* 1Password export.data (inside a .1pux): {accounts:[{vaults:[{items:[{overview,details}]}]}]} */
 if(d&&Array.isArray(d.accounts)){
  st.fmt='1Password';
  d.accounts.forEach(a=>(a.vaults||[]).forEach(v=>(v.items||[]).forEach(i=>{
   if(!i||i.trashed)return;const ov=i.overview||{},de=i.details||{},lf=de.loginFields||[];
   const pick=n=>{const f=lf.find(x=>x.designation===n);return f?f.value:''};
   let totp='';(de.sections||[]).forEach(sec=>(sec.fields||[]).forEach(f=>{if(f.value&&f.value.totp&&!totp)totp=f.value.totp}));
   if(i.categoryUuid==='003'||(!lf.length&&de.notesPlain&&!ov.url))add({note:1,title:ov.title,notes:de.notesPlain,folder:v.attrs&&v.attrs.name||''});
   else add({title:ov.title,url:ov.url||firstUrl((ov.urls||[]).map(u=>u.url)),user:pick('username'),pass:pick('password')||(de.password||''),totp,notes:de.notesPlain,folder:v.attrs&&v.attrs.name||''});
  })));
  return {out,st};
 }
 /* Proton Pass: {vaults:{id:{name,items:[{data:{type,metadata,content}}]}}} */
 if(d&&d.vaults&&!Array.isArray(d.vaults)&&typeof d.vaults==='object'){
  st.fmt='Proton Pass';
  Object.keys(d.vaults).forEach(k=>{const v=d.vaults[k];(v.items||[]).forEach(i=>{
   const x=i&&i.data;if(!x)return;const c=x.content||{},m=x.metadata||{};
   if(x.type==='login')add({title:m.name,url:firstUrl(c.urls||c.url),user:c.itemUsername||c.username||c.itemEmail||c.email,pass:c.password,totp:c.totpUri||c.totp,notes:m.note,folder:v.name});
   else if(x.type==='note')add({note:1,title:m.name,notes:m.note,folder:v.name});
   else st.skipped++;
  })});
  return {out,st};
 }
 /* Dashlane: {AUTHENTIFIANT:[...],SECURENOTE:[...]} */
 if(d&&(Array.isArray(d.AUTHENTIFIANT)||Array.isArray(d.SECURENOTE))){
  st.fmt='Dashlane';
  (d.AUTHENTIFIANT||[]).forEach(i=>add({title:i.title||i.domain,url:i.url||i.domain,user:i.login||i.email||i.secondaryLogin,pass:i.password,notes:i.note}));
  (d.SECURENOTE||[]).forEach(i=>add({note:1,title:i.title,notes:i.content||i.note}));
  return {out,st};
 }
 /* Keeper: {records:[{title,login,password,login_url,notes,custom_fields}]} */
 if(d&&Array.isArray(d.records)&&d.records.some(r=>r&&(r.login!==undefined||r.password!==undefined))){
  st.fmt='Keeper';
  d.records.forEach(r=>add({title:r.title,url:r.login_url||r.url,user:r.login,pass:r.password,notes:r.notes,folder:Array.isArray(r.folders)&&r.folders[0]?(r.folders[0].folder||r.folders[0].shared_folder||''):'',totp:r.custom_fields&&(r.custom_fields['TFC:Keeper']||r.custom_fields.totp||'')}));
  return {out,st};
 }
 /* Enpass: {items:[{title,category,note,fields:[{type,value}]}]} */
 if(d&&Array.isArray(d.items)&&d.items.length&&d.items.some(i=>i&&Array.isArray(i.fields)&&i.category)){
  st.fmt='Enpass';
  d.items.forEach(i=>{const f=t=>{const x=(i.fields||[]).find(y=>y.type===t&&y.value);return x?x.value:''};
   if(i.category==='note')add({note:1,title:i.title,notes:i.note});
   else if(['login','password'].includes(i.category))add({title:i.title,url:f('url'),user:f('username')||f('email'),pass:f('password'),totp:f('totp'),notes:i.note});
   else st.skipped++});
  return {out,st};
 }
 /* Anything else (KeePass, Chrome-style, hand-made files, other managers): walk the file and take every object that has a password plus a username, address or title. */
 st.fmt='JSON';
 const KS={title:['name','title','site','label','sitename','entryname','account','service'],url:['url','uri','website','loginuri','origin','domain','link','web','address','loginurl'],
  user:['username','user','login','loginusername','email','usernameoremail','userid','id','accountname','useremail'],pass:['password','pass','loginpassword','pwd','passwd'],
  totp:['totp','otp','otpauth','logintotp','twofactor','mfa','totpuri','totpsecret'],notes:['notes','note','comment','comments','extra','memo','description'],folder:['folder','group','category','vault','path']};
 const get=(m,k)=>{for(const n of KS[k]){if(m[n]!==undefined&&m[n]!==null&&typeof m[n]!=='object'&&String(m[n]).trim()!=='')return m[n]}return ''};
 const seen=new Set();let n=0;
 const walk=(o,depth)=>{
  if(!o||typeof o!=='object'||depth>8||n>20000||seen.has(o))return;seen.add(o);
  if(Array.isArray(o)){o.forEach(x=>walk(x,depth+1));return}
  const m=lc(o),pw=get(m,'pass');
  if(pw){n++;add({title:get(m,'title'),url:get(m,'url'),user:get(m,'user'),pass:pw,totp:get(m,'totp'),notes:get(m,'notes'),folder:get(m,'folder')});return}
  Object.keys(o).forEach(k=>walk(o[k],depth+1));
 };
 walk(d,0);
 return {out,st};
}
async function vJson(){
 if(!needUnlocked())return;
 if(!await confirmBox('A password export is plain text: anyone who gets the file can read every password. Import it, then delete the file from your phone. Choose the file now?','Choose file'))return;
 const txt=await pickFile('.json,application/json,text/plain,.txt');if(!txt)return;
 if(/^PK/.test(txt.slice(0,2)))return modal('This is a zip',`<p style="margin:0">1Password .1pux files are zip files. Unzip it, then choose the file named <b>export.data</b> inside.</p>`,[{label:'OK',cls:'pri'}]);
 let d;try{d=JSON.parse(txt.replace(/^﻿/,''))}catch(e){return modal('Not a JSON file',`<p style="margin:0">This file is not valid JSON. If it is a CSV export (Chrome, LastPass, NordPass, KeePassXC), use <b>Import CSV</b> instead.</p>`,[{label:'OK',cls:'pri'}])}
 const r=jsonLogins(d);
 if(r.st.enc)return modal('Encrypted export',`<p style="margin:0">This export is encrypted, so Festers cannot read it. Export it again without encryption (in Bitwarden choose <b>.json</b>, not <b>.json (Encrypted)</b>), import that file, then delete it.</p>`,[{label:'OK',cls:'pri'}]);
 let added=0,notes=0,dups=0,badTotp=0;
 r.out.forEach(o=>{
  let it;
  if(o.note){it={id:rid(),kind:'note',title:o.title||'Imported note',user:'',pass:'',totp:'',notes:o.notes,created:Date.now(),updated:Date.now()};
   if(V.items.some(x=>x.kind==='note'&&x.title===it.title&&x.notes===it.notes)){dups++;return}V.items.push(it);notes++;return}
  it={id:rid(),title:o.title||host(resolve(o.url))||'Imported',url:o.url,user:o.user,pass:o.pass,totp:o.totp,notes:o.notes,folder:o.folder||'',fav:o.fav||'',cf:o.cf||'',reprompt:'',created:Date.now(),updated:Date.now()};
  if(it.totp&&!parseTotp(it.totp)){it.totp='';badTotp++}
  if(V.items.some(x=>!x.kind&&x.title===it.title&&x.user===it.user&&x.url===it.url)){dups++;return}
  V.items.push(it);added++;
 });
 if(!added&&!notes){return modal('Nothing imported',`<p style="margin:0">No logins were found in this file${r.st.fmt?' ('+esc(r.st.fmt)+' format)':''}.${dups?' '+dups+' were already in the vault.':''}${r.st.skipped?' '+r.st.skipped+' items such as cards and identities are not imported.':''}</p>`,[{label:'OK',cls:'pri'}])}
 await vSave();if(panel==='vault')renderVault();
 try{const t=curTab();if(t&&/^about:preferences/.test(t.hist[t.pos]||''))renderPrefs(t,t.prefSec)}catch(e){}
 modal('Import finished',`<p style="margin:0 0 6px"><b>${added}</b> logins${notes?' and <b>'+notes+'</b> secure notes':''} imported${r.st.fmt?' from '+esc(r.st.fmt):''}.</p>${dups?`<p class="mu" style="margin:0 0 6px">${dups} were already in the vault.</p>`:''}${r.st.skipped?`<p class="mu" style="margin:0 0 6px">${r.st.skipped} items (cards, identities, empty entries) were skipped.</p>`:''}${badTotp?`<p class="mu" style="margin:0 0 6px">${badTotp} one-time-code keys could not be read and were left out.</p>`:''}<p style="margin:8px 0 0;color:var(--bad)"><b>Now delete the export file</b> from your phone: it holds your passwords in plain text.</p>`,[{label:'OK',cls:'pri'}]);
}

/* ---------- Autofill and passkeys status, with Conduint named ---------- */
async function afStatusDialog(P){
 const r=await P.autofillStatus();
 const pill=(k,t)=>{const c={ok:['rgba(18,161,80,.16)','var(--ok)'],warn:['rgba(255,176,0,.2)','#a86400'],bad:['rgba(225,29,46,.14)','var(--bad)']}[k];return `<span style="display:inline-block;padding:2px 9px;border-radius:999px;font-weight:700;font-size:12px;background:${c[0]};color:${c[1]}">${t}</span>`};
 const line=(k,v)=>`<div class="row2" style="justify-content:space-between;margin:0 0 8px;gap:10px"><span>${k}</span><span style="text-align:right">${v}</span></div>`;
 const short=v=>String(v||'').split(':').filter(Boolean).map(x=>x.split('/')[0]).join(', ');
 const afn=short(r.afService),crn=short(r.credService||r.credPrimary),web=S.passkeys!==false,cnd=!!r.conduintInstalled;
 const afOk=!!(r.conduintAfChosen||r.enabled||r.amEnabled);
 const rows=[];
 rows.push(line('Conduint',cnd?pill('ok','Installed'+(r.conduintVersion?' '+esc(r.conduintVersion):'')):pill('bad','Not found')));
 rows.push(line('Passkeys with Conduint',!cnd?pill('bad','Needs Conduint'):web?pill('ok','Ready'):pill('warn','Web passkeys off')));
 rows.push(line('Android passkey app',r.conduintCrChosen?pill('ok','Conduint'):crn?pill('warn',esc(crn)):(!r.settingsReadable&&r.conduintCrCapable)?pill('warn','Android hides it'):pill('warn','None chosen')));
 rows.push(line('Autofill service',r.conduintAfChosen?pill('ok','Conduint'):afOk?pill('ok',esc(afn||'On')):r.conduintAfCapable?pill('warn','Conduint can, not chosen'):pill('warn','Off')));
 rows.push(line('Phone supports autofill',pill(r.supported?'ok':'bad',r.supported?'Yes':'No')));
 const todo=[];
 if(!cnd)todo.push('Conduint was not found on this phone. Install Conduint V0.50.9 or newer. Festers can only see it if this build was made with the current setup script.');
 if(cnd&&!web)todo.push('Web passkeys is off. Turn it on in Settings > Autofill &amp; Passkeys.');
 if(cnd&&web)todo.push('<b>Passkeys with Conduint work without Android\'s passkey app choice.</b> Festers talks to Conduint directly: the first time a site asks for a passkey, Conduint shows the Festers certificate and you confirm once.');
 if(r.conduintAfCapable&&!r.conduintAfChosen)todo.push('To fill passwords with Conduint, tap <b>Choose autofill service</b> and pick Conduint.');
 if(cnd&&!r.conduintAfCapable)todo.push('This Conduint build does not offer an Android autofill service. Use Fill login in the Festers menu, or update Conduint.');
 if(!r.settingsReadable)todo.push('Android does not let apps read which passkey app and autofill service you chose, so those two lines show what Android allows, not your choice.');
 modal('Autofill and passkeys',`${rows.join('')}<div class="card" style="margin:8px 0">${todo.map(x=>`<p style="margin:0 0 6px">${x}</p>`).join('')}</div>${r.pkg?`<p class="mu" style="margin:0 0 4px"><small>Package</small> <b>${esc(String(r.pkg))}</b></p><p class="mu" style="margin:0;word-break:break-all"><small>Signing certificate SHA-256</small><br><b>${esc(String(r.cert||'could not be read'))}</b></p>`:''}`,
  [{label:'Choose autofill service',fn:()=>{Promise.resolve().then(()=>P.openAutofillSettings()).catch(()=>toast('Could not open settings'));return false}},
   {label:'Passkey manager',fn:()=>{Promise.resolve().then(()=>P[P.openPasskeySettings?'openPasskeySettings':'openAutofillSettings']()).catch(()=>toast('Could not open settings'));return false}},
   ...(cnd&&P.openConduint?[{label:'Open Conduint',fn:()=>{P.openConduint().then(x=>{if(x&&!x.ok)toast(x.error||'Could not open Conduint')}).catch(()=>{});return false}}]:[]),
   ...(r.cert?[{label:'Copy fingerprint',fn:()=>{copyText(String(r.cert));toast('Fingerprint copied');return false}}]:[]),{label:'Close'}]);
}
