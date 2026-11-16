/* Appointment workspace: journey, records and role/state-aware actions. All rules are enforced by the server. */
function appointmentWorkspace(){return {
  id:APPT.id,role:APPT.role,j:{},ctx:{},error:'',message:'',busy:false,recordTab:'Summary',rescheduling:false,rs:{date:'',time:''},
  recordTabs:['Summary','Visits','Notes','Diagnoses','Lab results','Imaging','Prescriptions','Certificates'],
  get p(){return this.j.patient||{}},get a(){return this.j.appointment||{}},get v(){return this.j.encounter||{}},get q(){return this.j.queue||{}},
  get today(){return new Date().toLocaleDateString('en-CA')},
  get clinical(){return ['Doctor','Nurse','Administrator','Hospital Administrator'].includes(this.role)},
  get doctorish(){return ['Doctor','Administrator','Hospital Administrator'].includes(this.role)},
  get flow(){return ['Doctor','Nurse','Receptionist','Administrator','Hospital Administrator'].includes(this.role)},
  get open(){return this.v.id&&!['Completed','Cancelled','Transferred'].includes(this.v.status)},
  get age(){return this.p.dateOfBirth?Math.floor((Date.now()-new Date(this.p.dateOfBirth))/31557600000):''},
  get hasAllergy(){const t=(this.p.allergies||'').trim();return !!t&&!/^(no known|none|nil|nkda|n\/a|no allergies)/i.test(t)},
  get vitals(){return (this.ctx.documents||[]).filter(d=>d.kind==='triage'&&d.encounter===this.v.id).at(-1)},
  initials(n){return (n||'').split(/\s+/).map(x=>x[0]).join('').slice(0,2).toUpperCase()},
  statusWord(s){return {done:'Done',current:'In progress',pending:'Pending',skipped:'Skipped',blocked:'Waiting on others',cancelled:'Cancelled'}[s]||s},
  async init(){await this.load()},
  async load(){
    try{this.j=await Clinic.api('/api/care/journey?id='+encodeURIComponent(this.id));this.error=''}catch(e){this.error=e.message;return}
    if(this.clinical&&this.p.id){try{this.ctx=await Clinic.api('/api/care/context?patient='+encodeURIComponent(this.p.id))}catch(e){this.ctx={}}}
  },
  workspace(tab){return '/care/patients/'+this.p.id+'/encounters/'+this.v.id+(tab?'?tab='+tab:'')},
  records(t){
    const h=this.ctx.history||{},docs=this.ctx.documents||[];
    const map={Visits:(h.visits||[]).map(r=>({id:r.id,title:r.name,sub:r.date+' · '+(r.doctorLabel||''),status:r.status})),
      Notes:docs.filter(d=>d.kind==='note').map(d=>({id:d.id,title:(d.assessment||d.subjective||'').slice(0,90),sub:d.date+' · '+d.encounter,status:d.status})),
      Diagnoses:docs.filter(d=>d.kind==='diagnosis').map(d=>({id:d.id,title:d.text,sub:d.date+' · '+d.encounter,status:d.status})),
      'Lab results':(h.laboratory||[]).map(r=>({id:r.id,title:r.name+(r.result?' — '+r.result:''),sub:r.date+' · '+(r.encounter||''),status:r.status})),
      Imaging:(h.imaging||[]).map(r=>({id:r.id,title:r.name+(r.impression?' — '+r.impression:''),sub:r.date+' · '+(r.encounter||''),status:r.status})),
      Prescriptions:(h.pharmacy||[]).map(r=>({id:r.id,title:(r.medicineLabel||r.medicine)+' · '+r.dosage,sub:r.date+' · '+r.frequency,status:r.status})),
      Certificates:docs.filter(d=>d.kind==='certificate').map(d=>({id:d.id,title:d.template,sub:d.date+' · '+d.encounter,status:d.status}))};
    return map[t]||[];
  },
  get clinicalActions(){
    if(!this.clinical)return [];if(!this.v.id)return this.p.id?[{key:'record',label:'Open Patient Record',go:'/care/patients/'+this.p.id}]:[];const a=[],ws=this.workspace.bind(this);
    if(this.open&&this.q.status==='Waiting')a.push({key:'call',label:'Call Patient',run:()=>this.queue('Called')});
    if(this.open&&this.doctorish&&this.v.status==='Waiting')a.push({key:'start',label:'Start Consultation',primary:true,run:()=>this.start()});
    a.push({key:'workspace',label:this.v.status==='In consultation'?'Open Consultation':'Open Encounter Workspace',primary:this.v.status==='In consultation',go:ws()});
    a.push({key:'record',label:'Open Patient Record',go:'/care/patients/'+this.p.id});
    if(this.open)a.push({key:'vitals',label:'Record Vitals',go:ws('triage')});
    if(this.open&&this.doctorish){
      a.push({key:'note',label:'Add Clinical Note',go:ws('note')},{key:'lab',label:'Create Laboratory Order',go:ws('lab')},{key:'imaging',label:'Create Imaging Order',go:ws('imaging')},
        {key:'rx',label:'Issue Prescription',go:ws('rx')},{key:'cert',label:'Create Medical Certificate',go:ws('cert')},{key:'review',label:'Review Results',go:ws('lab')},
        {key:'followup',label:'Schedule Follow-Up',go:ws('followup')},{key:'complete',label:'Complete Visit',go:ws('followup')});
    }
    return a;
  },
  get adminActions(){
    const a=[],acts=(this.a._actions||'').split('|').filter(Boolean),s=this.a.status;
    if(!this.a.id)return a;
    if(this.flow&&!this.a.encounter&&['Scheduled','Confirmed'].includes(s)&&this.a.date===this.today)a.push({key:'checkin',label:'Check In',primary:true,run:()=>this.checkin()});
    if(this.flow)a.push({key:'queue',label:'Open Queue',go:'/care/queue'});
    if(['Receptionist','Doctor','Administrator','Hospital Administrator'].includes(this.role))for(const [action,label] of [['Confirm','Confirm'],['Reschedule','Reschedule'],['Mark no-show','Mark No-Show'],['Cancel','Cancel Appointment']])if(acts.includes(action))a.push({key:action,label,danger:['Cancel','Mark no-show'].includes(action),run:()=>action==='Reschedule'?(this.rescheduling=true,this.rs={date:this.a.date,time:this.a.time}):this.transition(action)});
    if(['Receptionist','Doctor','Administrator','Hospital Administrator'].includes(this.role))a.push({key:'edit',label:'Edit Appointment',go:'/appointments/'+this.a.id});a.push({key:'print',label:'Print Details',run:()=>window.print()});
    return a;
  },
  canDo(action){return ({checkin:this.flow,call:this.flow,start:this.doctorish,document:this.doctorish,review:this.doctorish,complete:this.doctorish,vitals:this.clinical,reschedule:this.flow}[action])??false},
  nextLabel(){return Clinic.t({checkin:'Check In',call:'Call Patient',start:'Start Consultation',document:'Open Consultation',review:'Review Results',complete:'Complete Visit',vitals:'Record Vitals',reschedule:'Reschedule'}[this.j.next?.action]||'Open')},
  doNext(){const n=this.j.next?.action;({checkin:()=>this.checkin(),call:()=>this.queue('Called'),start:()=>this.start(),document:()=>location.href=this.workspace(),review:()=>location.href=this.workspace('lab'),complete:()=>location.href=this.workspace('followup'),vitals:()=>location.href=this.workspace('triage'),reschedule:()=>{this.rescheduling=true;this.rs={date:this.today,time:this.a.time}}}[n]||(()=>{}))()},
  async run(act){if(act.go){location.href=act.go;return}await act.run()},
  async guard(fn,ok){this.busy=true;this.error='';this.message='';try{await fn();this.message=Clinic.t(ok);await this.load()}catch(e){this.error=Clinic.t(e.message)}finally{this.busy=false}},
  checkin(){return this.guard(()=>Clinic.api('/api/care/action',{action:'checkin',patient:this.p.id,appointment:this.a.id,method:'Reception desk'}),'Patient checked in. A queue ticket was issued.')},
  queue(status){return this.guard(()=>Clinic.api('/api/care/action',{action:'queue',patient:this.p.id,encounter:this.v.id,queue:this.q.id,status}),'Queue updated.')},
  async start(){await this.guard(()=>Clinic.api('/api/care/action',{action:'start',patient:this.p.id,encounter:this.v.id}),'Consultation started.');if(!this.error)location.href=this.workspace('note')},
  transition(action){if(!confirm(Clinic.t('Confirm action?')))return;return this.guard(()=>Clinic.api('/api/records/appointments/'+this.a.id+'/actions',{action}),'Appointment updated.')},
  async reschedule(){await this.guard(()=>Clinic.api('/api/records/appointments/'+this.a.id+'/actions',{action:'Reschedule',...this.rs}),'Appointment rescheduled.');if(!this.error)this.rescheduling=false}
}}
