/* Shared helpers for the clinical pages and Mini Apps: API calls, status vocabulary, QR rendering and camera scanning.
   Business rules live in the Java services; these helpers only present data and send user actions. */
window.Clinic=(()=>{
  const csrf=()=>({[document.querySelector('meta[name=_csrf_header]')?.content||'X-CSRF-TOKEN']:document.querySelector('meta[name=_csrf]')?.content||''});
  async function api(url,body,method){
    const options={method:method||(body?'POST':'GET'),headers:{}};
    if(body){options.headers={'Content-Type':'application/json',...csrf()};options.body=JSON.stringify(body)}
    const response=await fetch(url,options);let data={};try{data=await response.json()}catch{}
    if(response.status===401&&!url.startsWith('/api/telegram/')){location.href='/login';throw Error('Your demo session expired. Sign in again.')}
    if(!response.ok)throw Error(data.message||'Request failed');return data;
  }
  const t=v=>window.CareI18n?CareI18n.t(v):v;
  const stepIcon={done:'✓',current:'●',pending:'○',skipped:'–',blocked:'!',cancelled:'×'};
  const tone=s=>({Waiting:'blue',Called:'amber',  'In service':'teal','On hold':'grey',Missed:'red',Completed:'green',Cancelled:'grey',Transferred:'grey',Scheduled:'blue',Confirmed:'teal','In consultation':'teal','No-Show':'red',Draft:'amber',Issued:'green',Signed:'green',Approved:'green',Pending:'amber',Urgent:'red',Emergency:'red',Normal:'grey'}[s]||'grey');
  const minutes=iso=>{if(!iso)return '—';const m=Math.max(0,Math.floor((Date.now()-new Date(iso))/60000));return m+(window.CareI18n?.language==='km'?' នាទី':' min')};
  /** Renders text as an inline SVG QR code (error correction M). Text never contains patient data. */
  function qrSvg(text,size=220){
    if(!window.qrcode||!text)return '';const q=qrcode(0,'M');q.addData(text);q.make();const n=q.getModuleCount(),cell=size/(n+8);let path='';
    for(let r=0;r<n;r++)for(let c=0;c<n;c++)if(q.isDark(r,c))path+=`M${((c+4)*cell).toFixed(2)} ${((r+4)*cell).toFixed(2)}h${cell.toFixed(2)}v${cell.toFixed(2)}h-${cell.toFixed(2)}z`;
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" style="width:${size}px;height:auto;max-width:100%;display:block" role="img" aria-label="QR code"><rect width="100%" height="100%" fill="#fff"/><path d="${path}" fill="#0b1f2e"/></svg>`;
  }
  /** Camera QR scanner. Uses BarcodeDetector when available, otherwise jsQR. Requires HTTPS or localhost. */
  function scanner(video,onCode){
    let stream=null,stopped=false,detector=null,canvas=document.createElement('canvas');
    async function start(){
      if(!window.isSecureContext||!navigator.mediaDevices?.getUserMedia)throw Error('Camera scanning needs HTTPS (or localhost). Enter the code manually instead.');
      stream=await navigator.mediaDevices.getUserMedia({video:{facingMode:'environment'},audio:false});video.srcObject=stream;video.setAttribute('playsinline','');await video.play();
      if('BarcodeDetector' in window){try{detector=new BarcodeDetector({formats:['qr_code']})}catch{detector=null}}
      requestAnimationFrame(tick);
    }
    async function tick(){
      if(stopped)return;
      try{
        if(video.readyState>=2){
          let value=null;
          if(detector){const codes=await detector.detect(video);value=codes[0]?.rawValue}
          else if(window.jsQR){canvas.width=video.videoWidth;canvas.height=video.videoHeight;const ctx=canvas.getContext('2d',{willReadFrequently:true});ctx.drawImage(video,0,0);const img=ctx.getImageData(0,0,canvas.width,canvas.height);value=jsQR(img.data,img.width,img.height)?.data}
          if(value){stop();onCode(value);return}
        }
      }catch{}
      setTimeout(()=>requestAnimationFrame(tick),180);
    }
    function stop(){stopped=true;stream?.getTracks().forEach(track=>track.stop());video.srcObject=null}
    return {start,stop};
  }
  /** Normalises scanned or typed check-in values: CPCI:<token>, arrive-<CODE>, Telegram/URL links with arrival codes. */
  function parseCode(raw){
    const v=String(raw||'').trim();if(!v)return {kind:'',value:''};
    if(/^CPCI:/i.test(v))return {kind:'ticket',value:v.slice(5)};
    const arrival=v.match(/(?:startapp=|arrival=)arrive-?([A-Za-z0-9]{8})|(?:^|\b)arrive-([A-Za-z0-9]{8})\b|[?&]arrival=([A-Za-z0-9]{8})/i);
    if(arrival)return {kind:'arrival',value:(arrival[1]||arrival[2]||arrival[3]).toUpperCase()};
    if(/^[0-9a-f-]{36}$/i.test(v))return {kind:'ticket',value:v};
    if(/^[A-Za-z0-9]{8}$/.test(v))return {kind:'arrival',value:v.toUpperCase()};
    return {kind:'unknown',value:v};
  }
  return {api,t,stepIcon,tone,minutes,qrSvg,scanner,parseCode};
})();
