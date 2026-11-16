function doctorSchedules(){return {
 role:SCHEDULE.role,doctor:SCHEDULE.doctor,doctors:[],date:new Date().toLocaleDateString('en-CA'),form:{},slots:[],closed:false,busy:false,error:'',message:'',request:0,
 get editable(){return ['Administrator','Hospital Administrator'].includes(this.role)},
 async init(){try{this.doctors=(await Clinic.api('/api/records/doctors?size=100')).items;await this.load()}catch(e){this.error=Clinic.t(e.message)}},
 async load(){const request=++this.request;this.busy=true;this.error='';this.message='';try{const r=await Clinic.api('/api/care/schedule?'+new URLSearchParams({doctor:this.doctor,date:this.date}));if(request!==this.request)return;this.form=r.schedule;this.slots=r.slots;this.closed=r.schedule.closed==='true'}catch(e){if(request===this.request)this.error=Clinic.t(e.message)}finally{if(request===this.request)this.busy=false}},
 async save(){this.busy=true;this.error='';try{await Clinic.api('/api/care/schedule',{...this.form,doctor:this.doctor,date:this.date,closed:String(this.closed),interval:String(this.form.interval)});await this.load();this.message=Clinic.t('Doctor schedule saved.')}catch(e){this.error=Clinic.t(e.message)}finally{this.busy=false}}
}}
