/* Staff queue dashboard. Station-based worklists over the shared queue service; the server enforces roles and transitions. */
function queueBoard(){
  const defaults={Doctor:'Consultation',Nurse:'Triage',Receptionist:'Consultation','Laboratory Staff':'Laboratory',Pharmacist:'Pharmacy',Cashier:'Payment'};
  const order={Emergency:0,Urgent:1,Normal:2};
  return {
  role:QUEUE.role,me:QUEUE.doctor,data:{queues:[],doctors:[],appointments:[],visits:[]},station:defaults[QUEUE.role]||'Consultation',doctor:'',priority:'',search:'',error:'',message:'',busy:false,transfer:{q:null,station:'Laboratory',doctor:''},timer:null,
  get stations(){return ({'Laboratory Staff':['Laboratory','Imaging'],Pharmacist:['Pharmacy'],Cashier:['Payment']})[this.role]||['Consultation','Triage','Laboratory','Imaging','Pharmacy','Payment','Doctor review']},
  get title(){return Clinic.t(this.role==='Doctor'?'My Queue':'Queue')+' · '+Clinic.t(this.station)},
  get canConsult(){return ['Doctor','Administrator','Hospital Administrator'].includes(this.role)},
  get canChart(){return ['Doctor','Nurse','Administrator','Hospital Administrator'].includes(this.role)},
  get canTransfer(){return ['Doctor','Nurse','Receptionist','Administrator','Hospital Administrator'].includes(this.role)},
  isConsult(q){return ['Registration','Triage','Consultation'].includes(q.station||'Consultation')},
  inStation(q){const st=q.station||'Consultation';if(this.station==='Consultation')return ['Registration','Triage','Consultation'].includes(st);if(this.station==='Triage')return ['Registration','Triage'].includes(st);return st===this.station},
  get mine(){const s=this.search.toLowerCase();return (this.data.queues||[]).filter(q=>q.encounter&&this.inStation(q)&&(!this.doctor||q.doctor===this.doctor)&&(!this.priority||q.priority===this.priority)&&(!s||(q.name+' '+(q.patientLabel||'')).toLowerCase().includes(s)))},
  sort(list){return list.sort((a,b)=>(order[a.priority]??2)-(order[b.priority]??2)||String(a.queuedAt||a.createdAt).localeCompare(String(b.queuedAt||b.createdAt)))},
  get waiting(){return this.sort(this.mine.filter(q=>q.status==='Waiting'))},
  get held(){return this.mine.filter(q=>q.status==='On hold')},
  get current(){return this.mine.filter(q=>['Called','In service'].includes(q.status)).sort((a,b)=>String(b.calledAt||'').localeCompare(String(a.calledAt||'')))[0]||null},
  get finished(){return this.mine.filter(q=>['Completed','Missed','Transferred','Cancelled'].includes(q.status)).slice(0,30)},
  get counts(){const c={};for(const q of this.mine)c[q.status]=(c[q.status]||0)+1;return ['Waiting','Called','In service','On hold','Missed','Transferred','Completed'].filter(k=>c[k]).map(k=>[k,c[k]])},
  appointmentOf(q){return (this.data.visits||[]).find(v=>v.id===q.encounter)?.appointment||(this.data.appointments||[]).find(a=>a.encounter===q.encounter)?.id||''},
  chart(q){return '/care/patients/'+q.patient+'/encounters/'+q.encounter},
  async init(){await this.load();this.timer=setInterval(()=>{if(!this.busy&&!this.transfer.q)this.load(false)},15000)},
  destroy(){clearInterval(this.timer)},
  async load(show=true){try{this.data={...this.data,...await Clinic.api('/api/care/mobile')};if(show)this.error=''}catch(e){if(show)this.error=Clinic.t(e.message)}},
  async guard(fn,ok){this.busy=true;this.error='';this.message='';try{await fn();this.message=Clinic.t(ok);await this.load()}catch(e){this.error=Clinic.t(e.message)}finally{this.busy=false}},
  callNext(){return this.guard(()=>Clinic.api('/api/care/queue/call-next',{station:this.station,doctor:this.role==='Doctor'?'':(this.doctor||'')}),'Next patient called.')},
  setStatus(q,status,ok){return this.guard(()=>Clinic.api('/api/care/action',{action:'queue',patient:q.patient,encounter:q.encounter,queue:q.id,status}),ok||'Queue updated.')},
  async start(q){await this.guard(async()=>{await Clinic.api('/api/care/action',{action:'start',patient:q.patient,encounter:q.encounter})},'Consultation started.');if(!this.error)location.href=this.chart(q)+'?tab=note'},
  openTransfer(q){this.transfer={q,station:this.station==='Consultation'?'Laboratory':'Consultation',doctor:q.doctor}},
  async doTransfer(){const q=this.transfer.q;await this.guard(()=>Clinic.api('/api/care/action',{action:'queue',patient:q.patient,encounter:q.encounter,queue:q.id,status:'Transferred',station:this.transfer.station,doctor:this.transfer.doctor}),'Patient transferred.');if(!this.error)this.transfer.q=null}
}}
