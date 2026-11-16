/* Doctor and patient Mini Apps. Same shared services as the desktop; the server scopes every response to the signed-in identity. */
function miniHospital(){
  const active=['Waiting','Called','In service','On hold'];
  return {
  mode:MINI.mode,role:MINI.role,me:MINI.doctor,auth:MINI.auth,
  data:{appointments:[],queues:[],visits:[],documents:[],laboratory:[],imaging:[],pharmacy:[],doctors:[],departments:[]},
  tab:'Home',lastTab:'Home',error:'',message:'',busy:false,timer:null,
  booking:{department:'',doctor:'',date:'',time:'',reason:''},bookStep:0,slots:[],apptView:'upcoming',rescheduleFor:'',reschedule:{date:'',time:''},
  journey:{},journeyFor:'',position:0,checkin:{open:false,mode:'scan',appointment:null,appointmentId:'',code:'',verified:false,token:'',expires:0},countdown:'',scanning:false,scan:null,
  transferQ:'',transferTo:{station:'Laboratory',doctor:''},summary:{},notifications:localStorage.getItem('careplus-reminders')==='on',
  get patient(){return this.mode==='patient'},
  get today(){return new Date().toLocaleDateString('en-CA')},
  get tabs(){return this.patient?[{key:'Home',label:'Home',icon:'house'},{key:'Book',label:'Book',icon:'calendar-plus'},{key:'Appointments',label:'Appointments',icon:'calendar-days'},{key:'Queue',label:'Queue',icon:'list-ordered'},{key:'Records',label:'Records',icon:'folder-heart'}]
    :[{key:'Home',label:'Home',icon:'house'},{key:'Queue',label:'Queue',icon:'list-ordered'},{key:'Schedule',label:'Schedule',icon:'calendar-days'},{key:'Tasks',label:'Tasks',icon:'list-checks'},{key:'Certificates',label:'Certificates',icon:'file-badge'}]},
  get maxBookStep(){const b=this.booking;return !b.department?0:!b.doctor?1:!b.time?2:!b.reason.trim()?3:4},
  name(list,id){return (list||[]).find(x=>x.id===id)?.name||''},
  statusWord(s){return {done:'Done',current:'In progress',pending:'Pending',skipped:'Skipped',blocked:'Waiting on others',cancelled:'Cancelled'}[s]||s},
  // ----- patient views
  get activeQueue(){return (this.data.queues||[]).filter(q=>active.includes(q.status)).sort((a,b)=>String(b.queuedAt||'').localeCompare(String(a.queuedAt||'')))[0]||null},
  get upcoming(){return (this.data.appointments||[]).filter(a=>a.date>=this.today&&['Scheduled','Confirmed'].includes(a.status)&&!a.encounter).sort((a,b)=>(a.date+a.time).localeCompare(b.date+b.time))},
  get nextAppointment(){return this.upcoming[0]||null},
  get apptList(){const all=[...(this.data.appointments||[])].sort((a,b)=>(a.date+a.time).localeCompare(b.date+b.time));return this.apptView==='upcoming'?all.filter(a=>a.date>=this.today&&!['Completed','Cancelled','No-Show'].includes(a.status)):all.filter(a=>a.date<this.today||['Completed','Cancelled','No-Show'].includes(a.status)).reverse()},
  get checkinCandidates(){return (this.data.appointments||[]).filter(a=>this.canCheckIn(a))},
  canCheckIn(a){return a&&!a.encounter&&a.date===this.today&&['Scheduled','Confirmed'].includes(a.status)},
  get recordGroups(){return [['visits','Visit history',this.data.visits||[]],['laboratory','Laboratory results',this.data.laboratory||[]],['imaging','Imaging reports',this.data.imaging||[]],['pharmacy','Prescriptions',this.data.pharmacy||[]],['documents','Medical certificates',this.data.documents||[]]]},
  // ----- doctor views
  get docQueue(){const o={Emergency:0,Urgent:1,Normal:2},rank={Called:0,'In service':0,Waiting:1,'On hold':2};return (this.data.queues||[]).filter(q=>q.encounter&&active.includes(q.status)&&['Registration','Triage','Consultation'].includes(q.station||'Consultation')).sort((a,b)=>rank[a.status]-rank[b.status]||(o[a.priority]??2)-(o[b.priority]??2)||String(a.queuedAt).localeCompare(String(b.queuedAt)))},
  get docWaiting(){return this.docQueue.filter(q=>q.status==='Waiting')},
  get docCurrent(){return this.docQueue.find(q=>['Called','In service'].includes(q.status))||null},
  get todayAppointments(){return (this.data.appointments||[]).filter(a=>a.date===this.today&&a.status!=='Cancelled').sort((a,b)=>a.time.localeCompare(b.time))},
  get upcomingAppointments(){return (this.data.appointments||[]).filter(a=>a.date>this.today&&['Scheduled','Confirmed'].includes(a.status)).sort((a,b)=>(a.date+a.time).localeCompare(b.date+b.time)).slice(0,10)},
  ws(p,e,tab){return '/care/patients/'+p+(e?'/encounters/'+e:'')+(tab?'?tab='+tab:'')},
  get draftNotes(){return (this.data.documents||[]).filter(d=>d.kind==='note'&&d.status==='Draft').map(d=>({id:d.id,title:(d.subjective||'Draft note').slice(0,60),sub:d.patient+' · '+d.date,href:this.ws(d.patient,d.encounter,'note')}))},
  get resultsToReview(){return [...(this.data.laboratory||[]),...(this.data.imaging||[])].filter(r=>r.status==='Approved'&&r.reviewedByDoctor!=='true'&&r.encounter).map(r=>({id:r.id,title:r.name,sub:r.patientLabel+' · '+r.date,href:this.ws(r.patient,r.encounter,r.id.startsWith('IMG')?'imaging':'lab')}))},
  get pendingOrders(){return [...(this.data.laboratory||[]),...(this.data.imaging||[])].filter(r=>!['Approved','Cancelled'].includes(r.status)&&r.encounter).map(r=>({id:r.id,title:r.name+' · '+CareI18n.t(r.status),sub:r.patientLabel,href:this.ws(r.patient,r.encounter,r.id.startsWith('IMG')?'imaging':'lab')}))},
  get openConsultations(){return (this.data.visits||[]).filter(v=>v.status==='In consultation').map(v=>({id:v.id,title:v.patientLabel,sub:v.name+' · '+v.date,href:this.ws(v.patient,v.id)}))},
  get followUps(){return (this.data.appointments||[]).filter(a=>a.type==='Follow-up'&&a.date>=this.today&&a.status!=='Cancelled').map(a=>({id:a.id,title:a.patientLabel,sub:a.date+' · '+a.time,href:'/care/appointments/'+a.id}))},
  get certificates(){return (this.data.documents||[]).filter(d=>d.kind==='certificate').reverse()},
  get doctorName(){return this.name(this.data.doctors,this.me)||this.me},
  get summaryVitals(){const v=(this.summary.documents||[]).filter(d=>d.kind==='triage').at(-1);return v?[v.bloodPressure&&v.bloodPressure+' mmHg',v.temperature&&v.temperature+' °C',v.pulse&&v.pulse+' bpm'].filter(Boolean).join(' · '):'—'},
  workspace(q){return this.ws(q.patient,q.encounter)},
  // ----- lifecycle
  async init(){
    const q=new URLSearchParams(location.search);const requested=q.get('tab');if(this.tabs.some(t=>t.key===requested)||requested==='Profile')this.tab=requested;
    await this.load();
    const arrival=q.get('arrival');if(this.patient&&arrival)this.openArrivalScan(arrival);
    const tg=window.Telegram?.WebApp;
    if(tg&&tg.initData){tg.ready();tg.expand();tg.onEvent('themeChanged',()=>{if((localStorage.getItem('careplus-theme')||'system')==='system')CareI18n.applyTheme()});
      const sync=()=>document.documentElement.style.setProperty('--tg-height',(tg.viewportStableHeight||innerHeight)+'px');tg.onEvent('viewportChanged',sync);sync();
      tg.BackButton?.onClick(()=>{if(this.checkin.open)this.closeCheckin();else this.go('Home')});this.$watch('tab',v=>v==='Home'?tg.BackButton?.hide():tg.BackButton?.show())}
    this.timer=setInterval(()=>{if(!this.busy&&!this.checkin.open)this.load(false)},15000);
  },
  destroy(){clearInterval(this.timer);this.stopScan()},
  go(t){if(t!=='Summary')this.lastTab=this.tab==='Summary'?this.lastTab:this.tab;this.tab=t;this.error='';this.message='';window.scrollTo({top:0})},
  async load(show=true){try{this.data={...this.data,...await Clinic.api('/api/care/mobile')};if(show)this.error='';await this.loadPosition()}catch(e){if(show)this.error=Clinic.t(e.message)}},
  async loadPosition(){if(!this.patient||!this.activeQueue){this.position=0;return}try{const j=await Clinic.api('/api/care/journey?id='+this.activeQueue.encounter);this.position=j.position||0}catch{this.position=0}},
  async guard(fn,ok){this.busy=true;this.error='';this.message='';try{await fn();if(ok)this.message=Clinic.t(ok);await this.load()}catch(e){this.error=Clinic.t(e.message)}finally{this.busy=false}},
  // ----- patient actions
  async loadSlots(){this.booking.time='';this.slots=[];if(this.booking.doctor&&this.booking.date)try{this.slots=await Clinic.api('/api/care/slots?'+new URLSearchParams({doctor:this.booking.doctor,date:this.booking.date}))}catch(e){this.error=Clinic.t(e.message)}},
  async book(){await this.guard(()=>Clinic.api('/api/care/booking',this.booking),'Appointment booked.');if(!this.error){this.booking={department:'',doctor:'',date:'',time:'',reason:''};this.bookStep=0;this.apptView='upcoming';this.tab='Appointments'}},
  async patientAction(a,action){if(!confirm(Clinic.t(action==='Cancel'?'Cancel this appointment?':'Confirm action?')))return;await this.guard(()=>Clinic.api('/api/care/patient-appointment',{appointment:a.id,action,...this.reschedule}),action==='Cancel'?'Appointment cancelled.':'Appointment rescheduled.');this.rescheduleFor=''},
  async toggleJourney(a){if(this.journeyFor===a.id){this.journeyFor='';return}try{this.journey=await Clinic.api('/api/care/journey?id='+a.id);this.journeyFor=a.id}catch(e){this.error=Clinic.t(e.message)}},
  openCheckin(a){this.checkin={open:true,mode:'scan',appointment:a,appointmentId:a?.id||'',code:'',verified:false,token:'',expires:0}},
  openArrivalScan(code=''){const c=this.checkinCandidates;this.checkin={open:true,mode:'scan',appointment:c.length===1?c[0]:null,appointmentId:c[0]?.id||'',code:String(code||'').toUpperCase(),verified:false,token:'',expires:0};if(!c.length)this.error=Clinic.t('You have no appointment to check in for today.')},
  closeCheckin(){this.stopScan();this.checkin.open=false;clearInterval(this.tick)},
  async startScan(){this.scanning=true;await this.$nextTick();this.scan=Clinic.scanner(this.$refs.video,value=>{this.scanning=false;const p=Clinic.parseCode(value);if(p.kind==='arrival')this.checkin.code=p.value;else this.error=Clinic.t('That QR code is not a CarePlus arrival code.')});try{await this.scan.start()}catch(e){this.scanning=false;this.error=Clinic.t(e.message)}},
  stopScan(){this.scan?.stop();this.scanning=false},
  async arrive(){const id=this.checkin.appointment?.id||this.checkin.appointmentId;const code=Clinic.parseCode(this.checkin.code).value||this.checkin.code;
    await this.guard(()=>Clinic.api('/api/care/arrival-checkin',{appointment:id,code}),'You are checked in. Your queue number is shown below.');if(!this.error){this.closeCheckin();this.tab='Queue'}},
  async showTicket(refresh){this.checkin.mode='ticket';if(this.checkin.token&&!refresh&&this.checkin.expires>Date.now())return;const id=this.checkin.appointment?.id||this.checkin.appointmentId;
    try{const r=await Clinic.api('/api/care/checkin',{appointment:id,verified:'true'});this.checkin.token=r.token;this.checkin.expires=Number(r.expires);clearInterval(this.tick);
      this.tick=setInterval(()=>{const s=Math.max(0,Math.round((this.checkin.expires-Date.now())/1000));this.countdown=Math.floor(s/60)+':'+String(s%60).padStart(2,'0');if(!s){this.checkin.token='';clearInterval(this.tick)}if(this.checkin.open)this.load(false).then(()=>{if(!this.canCheckIn((this.data.appointments||[]).find(a=>a.id===id))){this.closeCheckin();this.message=Clinic.t('You are checked in. Your queue number is shown below.');this.tab='Queue'}})},1000)}
    catch(e){this.error=Clinic.t(e.message)}},
  // ----- doctor actions
  callNext(){return this.guard(()=>Clinic.api('/api/care/queue/call-next',{station:'Consultation'}),'Next patient called.')},
  setQueue(q,status){return this.guard(()=>Clinic.api('/api/care/action',{action:'queue',patient:q.patient,encounter:q.encounter,queue:q.id,status}),'Queue updated.')},
  async start(q){await this.guard(()=>Clinic.api('/api/care/action',{action:'start',patient:q.patient,encounter:q.encounter}),'Consultation started.');if(!this.error)location.href=this.ws(q.patient,q.encounter,'note')},
  async transfer(q){await this.guard(()=>Clinic.api('/api/care/action',{action:'queue',patient:q.patient,encounter:q.encounter,queue:q.id,status:'Transferred',station:this.transferTo.station,doctor:this.transferTo.doctor||q.doctor}),'Patient transferred.');this.transferQ=''},
  async openSummary(q){try{const c=await Clinic.api('/api/care/context?'+new URLSearchParams({patient:q.patient,encounter:q.encounter||''}));this.summary={...c,href:this.ws(q.patient,q.encounter)};this.go('Summary')}catch(e){this.error=Clinic.t(e.message)}},
  async issue(d){if(!confirm(Clinic.t('Confirm clinician review and finalize this demo document?')))return;await this.guard(()=>Clinic.api('/api/care/action',{action:'issue',patient:d.patient,encounter:d.encounter,document:d.id,reviewed:'true'}),'Certificate issued.')},
  printCertificate(d){const f=document.createElement('iframe');f.hidden=true;document.body.append(f);const doc=f.contentDocument;doc.open();doc.write('<!doctype html><html><head><meta charset="utf-8"><title>CarePlus</title><style>@page{size:A4;margin:20mm}body{font:14px/1.7 "Noto Sans Khmer",Inter,Arial,sans-serif;color:#111;background:#fff}h1{color:#0f766e;margin:0}h2{text-align:center;text-transform:uppercase}.box{border:1px solid #cbd5e1;padding:12px;white-space:pre-wrap;min-height:100px}.w{position:fixed;top:45%;left:0;right:0;text-align:center;font-size:56px;color:rgba(185,28,28,.12);transform:rotate(-24deg);font-weight:800}</style></head><body></body></html>');doc.close();
    const el=(t,x,c)=>{const e=doc.createElement(t);e.textContent=x;if(c)e.className=c;doc.body.append(e)};el('div',d.status==='Issued'?'DEMO · NOT VALID FOR OFFICIAL USE':'DRAFT · NOT ISSUED','w');el('h1','CarePlus General Hospital');el('p','Central campus · Synthetic demonstration document');el('h2',d.template||'Medical certificate');
    el('p',(this.patient?this.data.patient?.name+' · ':'')+d.patient+' · '+d.encounter+' · '+d.date);if(d.purpose)el('p','Purpose: '+d.purpose);if(d.leaveStart)el('p','Leave: '+d.leaveStart+' → '+(d.leaveEnd||''));el('div',d.text||'','box');el('p','Document '+d.id+' · '+d.status+(d.signedAt?' · '+d.signedAt.slice(0,16).replace('T',' '):''));el('p','Synthetic data. Not valid for clinical, legal, employment or insurance use.');
    f.contentWindow.focus();f.contentWindow.print();setTimeout(()=>f.remove(),60000)},
  async savePreferences(){localStorage.setItem('careplus-reminders',this.notifications?'on':'off');
    if(this.auth==='telegram')await this.guard(()=>Clinic.api('/api/telegram/notifications',{enabled:String(this.notifications)}),'Notification preference saved.');
    else this.message=Clinic.t('Preference saved on this device. Telegram reminders need a linked Telegram account.')}
}}
