/* Telegram Mini App administration. The bot token never reaches the browser; this page only edits public settings. */
function telegramSettings(){return {
  s:{},bot:{},command:'/start',previewLanguage:'en',reply:'',form:{botUsername:'',shortName:'',publicUrl:'',contactInfo:''},doctors:[],doctor:'',code:{},busy:false,error:'',message:'',poster:{},
  get links(){return this.s.deepLinks||{}},
  get qrItems(){return [
    {key:'patient',title:'Patient access QR',link:this.links.patient,note:'For posters in waiting areas. Patients link their account once with a code from reception.',km:'ស្កេនដើម្បីបើក CarePlus សម្រាប់អ្នកជំងឺ'},
    {key:'doctor',title:'Doctor access QR',link:this.links.doctor,note:'For staff areas. Scanning does not grant doctor access; an administrator must link the doctor.',km:'ស្កេនដើម្បីបើក CarePlus សម្រាប់វេជ្ជបណ្ឌិត'},
    {key:'arrival',title:'Hospital arrival QR',link:this.links.arrival,note:'For reception. Patients scan to check in for today\'s appointment. Changes daily.',km:'មកដល់សម្រាប់ការណាត់ជួប? ស្កេនដើម្បីបញ្ជាក់ការមកដល់។'}].filter(i=>i.link)},
  async init(){await this.load();try{this.doctors=(await Clinic.api('/api/records/doctors?size=100')).items}catch{}},
  async load(){try{this.s=await Clinic.api('/api/telegram/settings');this.bot=await Clinic.api('/api/telegram/bot-status');this.form={botUsername:this.s.botUsername,shortName:this.s.shortName,publicUrl:this.s.publicUrl,contactInfo:this.s.contactInfo}}catch(e){this.error=Clinic.t(e.message)}},
  testBot(){return this.guard(async()=>{const r=await Clinic.api('/api/telegram/bot-preview',{command:this.command,language:this.previewLanguage});this.reply=r.text},'Command preview prepared. No message was sent.')},
  async guard(fn,ok){this.busy=true;this.error='';this.message='';try{await fn();if(ok)this.message=Clinic.t(ok);await this.load()}catch(e){this.error=Clinic.t(e.message)}finally{this.busy=false}},
  save(){return this.guard(()=>Clinic.api('/api/telegram/settings',this.form),'Telegram configuration saved.')},
  doctorCode(){return this.guard(async()=>{this.code=await Clinic.api('/api/telegram/link-code',{role:'Doctor',identity:this.doctor})})},
  unlink(a){if(!confirm(Clinic.t('Remove this Telegram link?')))return;return this.guard(()=>Clinic.api('/api/telegram/unlink',{identity:a.identity}),'Telegram link removed.')},
  async copy(link){try{await navigator.clipboard.writeText(link);this.message=Clinic.t('Link copied.')}catch{this.message=link}},
  print(item){this.poster={heading:'CarePlus General Hospital',title:item.title,link:item.link,km:item.km};this.$nextTick(()=>{document.body.classList.add('printing-poster');window.print();setTimeout(()=>document.body.classList.remove('printing-poster'),500)})}
}}
