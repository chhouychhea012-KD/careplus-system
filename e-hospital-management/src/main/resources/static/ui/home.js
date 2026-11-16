/* Role home dashboard. Every figure comes from /api/care/dashboard (live service data); nothing is estimated here. */
/* The Chart.js instance stays outside Alpine's reactive state (proxying it recurses). */
let homeChart=null;
function roleHome(){
  const blocksByRole={
    Doctor:[{key:'schedule',title:"Today's schedule",empty:'No appointments today.',more:'/appointments?doctor='+HOME.doctor},
            {key:'tasks',title:'Tasks',empty:'Nothing needs your attention.',more:'/care/tasks'},
            {key:'upcoming',title:'Upcoming appointments',empty:'No upcoming appointments.',more:'/appointments?doctor='+HOME.doctor}],
    Receptionist:[{key:'arrivals',title:'Expected arrivals',empty:'Everyone expected today has arrived.',more:'/care/checkin',moreLabel:'Check-in desk'},
                  {key:'checkins',title:'Recent check-ins',empty:'No check-ins yet today.'}],
    Nurse:[{key:'triage',title:'Waiting for triage',empty:'Nobody is waiting for triage.',more:'/care/queue',moreLabel:'Open queue'}],
    'Laboratory Staff':[{key:'worklist',title:'Laboratory worklist',empty:'No open laboratory orders.',more:'/laboratory'}],
    'Imaging Staff':[{key:'worklist',title:'Imaging Worklist',empty:'No open imaging orders.',more:'/imaging'}],
    Pharmacist:[{key:'worklist',title:'Prescriptions to handle',empty:'No prescriptions waiting.',more:'/pharmacy'}],
    Cashier:[{key:'worklist',title:'Open invoices',empty:'No open invoices.',more:'/billing'}]
  };
  const intros={Doctor:'Your patients, queue and tasks for today.',Receptionist:'Arrivals, check-ins and the waiting room at a glance.',Nurse:'Patients waiting for triage and vital signs.',
    'Laboratory Staff':'Samples to collect and results to enter.',Pharmacist:'Prescriptions to review and dispense, and stock alerts.',Cashier:'Invoices to issue and payments to collect.'};
  return {
    role:HOME.role,d:{},loading:true,busy:false,error:'',message:'',timer:null,
    get blocks(){return blocksByRole[this.role]||[]},
    get intro(){return intros[this.role]||'Hospital activity, staffing, queues and billing today.'},
    get greeting(){const h=new Date().getHours();return Clinic.t(h<12?'Good morning':h<18?'Good afternoon':'Good evening')},
    get dateLabel(){try{return new Date().toLocaleDateString(window.CareI18n?.language==='km'?'km-KH':'en-GB',{weekday:'long',day:'numeric',month:'long',year:'numeric'})}catch{return new Date().toDateString()}},
    barWidth(n,all){const max=Math.max(1,...Object.values(all||{}));return Math.round(n/max*100)},
    async init(){await this.load();this.timer=setInterval(()=>{if(!this.busy)this.load(false)},30000);
      window.addEventListener('careplus-theme',()=>this.drawChart());window.addEventListener('careplus-language',()=>setTimeout(()=>this.drawChart(),50))},
    async load(show=true){try{this.d=await Clinic.api('/api/care/dashboard');this.error=''}catch(e){if(show)this.error=Clinic.t(e.message)}finally{this.loading=false;this.$nextTick(()=>{Clinic.icons();this.drawChart()})}},
    async callNext(){this.busy=true;this.error='';this.message='';try{const r=await Clinic.api('/api/care/queue/call-next',{station:'Consultation',doctor:''});this.message=Clinic.t('Next patient called.')+(r.name?' '+r.name:'');await this.load()}catch(e){this.error=Clinic.t(e.message)}finally{this.busy=false}},
    drawChart(){
      const el=document.getElementById('deptChart');if(!el||!this.d.departments||!window.Chart)return;
      const css=getComputedStyle(document.body);const v=n=>css.getPropertyValue(n).trim();
      const labels=Object.keys(this.d.departments).map(k=>Clinic.t(k));
      const data={labels,datasets:[{label:Clinic.t('Appointments'),data:Object.values(this.d.departments).map(x=>x.appointments),backgroundColor:v('--primary')||'#0e7490',borderRadius:4},
        {label:Clinic.t('Patient visits'),data:Object.values(this.d.departments).map(x=>x.visits),backgroundColor:v('--info')||'#3b82f6',borderRadius:4}]};
      Chart.defaults.font.family=getComputedStyle(document.body).fontFamily;
      const grid=v('--border')||'#e5e7eb',text=v('--muted')||'#64748b';
      const options={responsive:true,maintainAspectRatio:false,indexAxis:'y',plugins:{legend:{labels:{color:text,boxWidth:10}}},scales:{x:{beginAtZero:true,ticks:{precision:0,color:text},grid:{color:grid}},y:{ticks:{color:text},grid:{display:false}}}};
      if(homeChart){homeChart.destroy()}
      homeChart=new Chart(el,{type:'bar',data:JSON.parse(JSON.stringify(data)),options});
    }
  };
}
