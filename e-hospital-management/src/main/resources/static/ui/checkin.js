/* Reception check-in desk: scan/redeem appointment tickets, list today's arrivals, arrival poster and Telegram link codes. */
function checkinDesk(){return {
  role:DESK.role,code:'',busy:false,error:'',result:null,scanning:false,scan:null,expected:[],history:[],search:'',arrival:{},links:{},patients:[],linkPatient:'',link:{},
  get canPoster(){return ['Receptionist','Administrator','Hospital Administrator'].includes(this.role)},
  get arrivalLink(){return this.links.arrival||''},
  get expectedFiltered(){const s=this.search.toLowerCase();return this.expected.filter(a=>!s||(a.patientLabel+' '+a.id).toLowerCase().includes(s))},
  async init(){await this.load()},
  async load(){
    const today=new Date().toLocaleDateString('en-CA');
    try{
      const [list,history]=await Promise.all([Clinic.api('/api/records/appointments?'+new URLSearchParams({from:today,to:today,size:100,filter:'arrivalStatus=Waiting for Check-In'})),Clinic.api('/api/care/checkin/history')]);
      this.expected=list.items.filter(a=>['Scheduled','Confirmed'].includes(a.status)).sort((a,b)=>a.time.localeCompare(b.time));this.history=history;
      if(this.canPoster){const s=await Clinic.api('/api/telegram/settings');this.arrival=s.arrival;this.links=s.deepLinks;if(!this.patients.length)this.patients=(await Clinic.api('/api/records/patients?size=100')).items}
    }catch(e){this.error=Clinic.t(e.message)}
  },
  async done(visit){this.result=visit;this.code='';await this.load()},
  async guard(fn){this.busy=true;this.error='';try{await fn()}catch(e){this.error=Clinic.t(e.message)}finally{this.busy=false}},
  submitCode(){const parsed=Clinic.parseCode(this.code);if(parsed.kind==='arrival'){this.error=Clinic.t('This is the hospital arrival code, not a patient ticket. Ask the patient to show their appointment QR.');return}
    return this.guard(async()=>this.done(await Clinic.api('/api/care/checkin/scan',{code:parsed.value})))},
  checkin(a){return this.guard(async()=>this.done(await Clinic.api('/api/care/action',{action:'checkin',patient:a.patient,appointment:a.id,method:'Reception desk'})))},
  async startScan(){this.error='';this.scanning=true;await this.$nextTick();this.scan=Clinic.scanner(this.$refs.video,value=>{this.scanning=false;this.code=value;this.submitCode()});try{await this.scan.start()}catch(e){this.scanning=false;this.error=Clinic.t(e.message)}},
  stopScan(){this.scan?.stop();this.scanning=false},
  rotate(){if(!confirm(Clinic.t('Rotate the arrival code? Printed posters will stop working.')))return;return this.guard(async()=>{await Clinic.api('/api/care/arrival/rotate',{});await this.load()})},
  linkCode(){return this.guard(async()=>{this.link=await Clinic.api('/api/telegram/link-code',{role:'Patient',identity:this.linkPatient})})},
  printPoster(){document.body.classList.add('printing-poster');window.print();setTimeout(()=>document.body.classList.remove('printing-poster'),500)}
}}
