/* Certificate history and doctor task list. Data: /api/care/documents, /api/care/mobile, /api/care/dashboard. */
function certificateList(){return{
  docs:[],visits:[],q:'',status:'',error:'',
  get open(){return this.visits.filter(v=>['Waiting','In consultation'].includes(v.status))},
  get filtered(){const s=this.q.toLowerCase();return this.docs.filter(d=>(!this.status||d.status===this.status)&&(!s||(d.patientLabel+' '+d.id+' '+d.template+' '+d.encounter).toLowerCase().includes(s)))},
  async init(){try{const [docs,ctx]=await Promise.all([Clinic.api('/api/care/documents?kind=certificate'),Clinic.api('/api/care/mobile')]);this.docs=docs;this.visits=ctx.visits||[]}catch(e){this.error=Clinic.t(e.message)}this.$nextTick(Clinic.icons)}
}}
function taskList(){return{
  tasks:[],kind:'all',loading:true,error:'',
  kinds:[['all','All'],['note','Clinical notes'],['result','Results'],['certificate','Certificates']],
  type(t){return /^Review result/.test(t.title)?'result':/certificate/i.test(t.title)?'certificate':'note'},
  countOf(k){return k==='all'?this.tasks.length:this.tasks.filter(t=>this.type(t)===k).length},
  get filtered(){return this.kind==='all'?this.tasks:this.tasks.filter(t=>this.type(t)===this.kind)},
  async init(){try{this.tasks=(await Clinic.api('/api/care/dashboard')).lists?.tasks||[]}catch(e){this.error=Clinic.t(e.message)}finally{this.loading=false;this.$nextTick(Clinic.icons)}}
}}
