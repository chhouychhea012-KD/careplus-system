/* Telegram Mini App entry. Sends the raw, signed initData to the server; initDataUnsafe is never trusted. */
function telegramEntry(){return {
  state:'loading',error:'',code:'',busy:false,firstName:'',
  get start(){const tg=window.Telegram?.WebApp;return tg?.initDataUnsafe?.start_param||new URLSearchParams(location.search).get('startapp')||new URLSearchParams(location.search).get('tgWebAppStartParam')||''},
  get arrival(){const m=this.start.match(/^arrive-([A-Za-z0-9]{8})$/);return m?m[1].toUpperCase():''},
  get demoLink(){return this.start==='doctor'?'/mini/doctor':'/mini/patient'+(this.arrival?'?arrival='+this.arrival:'')},
  async init(){
    const tg=window.Telegram?.WebApp;
    if(tg){tg.ready();tg.expand();if(tg.colorScheme&&(localStorage.getItem('careplus-theme')||'system')==='system')window.CareI18n?.applyTheme()}
    await this.begin();
  },
  async begin(){
    const raw=window.Telegram?.WebApp?.initData||'';this.error='';
    if(!raw||!TGENTRY.configured){this.state='demo';return}
    this.state='loading';
    try{const r=await Clinic.api('/api/telegram/session',{initData:raw});if(r.status==='signed-in'){location.replace(r.redirect);return}this.firstName=r.firstName||'';this.state='link'}
    catch(e){this.error=Clinic.t(e.message);this.state='error'}
  },
  async link(){this.busy=true;this.error='';try{const r=await Clinic.api('/api/telegram/link',{code:this.code});location.replace(r.redirect)}catch(e){this.error=Clinic.t(e.message);this.state='error'}finally{this.busy=false}}
}}
