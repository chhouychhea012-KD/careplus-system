// End-to-end patient journey across roles, plus Khmer / dark-mode / mobile sweeps.
// Usage: start the app, then `node scripts/journey-test.cjs` (PLAYWRIGHT_CHROMIUM_EXECUTABLE, HOSPITAL_URL optional).
const {chromium}=require('playwright');const fs=require('fs'),assert=require('assert');
const base=process.env.HOSPITAL_URL||'http://127.0.0.1:8090';
const results={assertions:[],untranslated:{},lightSurfacesInDark:{},overflow:{},errors:[]};
const ok=(v,m)=>{assert(v,m);results.assertions.push(m)};
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE||undefined});
 const context=await browser.newContext({viewport:{width:1440,height:1000}});await context.route('https://**/*',r=>r.abort());
 const page=await context.newPage();page.on('pageerror',e=>results.errors.push(e.message));
 page.on('dialog',d=>d.accept(d.type()==='prompt'?'Duplicate synthetic order':undefined));
 const state=()=>page.evaluate(()=>Alpine.$data(document.body));
 const login=async(role,identity)=>{await page.goto(base+'/login');await page.locator('select[name=role]').selectOption(role);if(identity)await page.locator('select[name=patientIdentity]').selectOption(identity);await page.locator('form[action="/login"] button[type=submit]').click();await page.waitForLoadState('networkidle');await page.waitForFunction(()=>window.Alpine&&document.body._x_dataStack)};
 const csrf=()=>page.locator('meta[name=_csrf]').getAttribute('content');
 const call=async(method,url,body)=>{const r=await context.request.fetch(base+url,{method,headers:{'X-CSRF-TOKEN':await csrf()},data:body});const t=await r.text();if(!r.ok())throw Error(method+' '+url+' -> '+r.status()+' '+t);return JSON.parse(t)};
 const ready=async()=>{await page.waitForLoadState('networkidle');await page.waitForFunction(()=>window.Alpine&&document.body._x_dataStack)};
 const quick=async name=>{await Promise.all([page.waitForURL(u=>true),page.locator('.nav2 .nav-link',{hasText:name}).first().click()]);await ready()};
 const go=async url=>{await page.goto(base+url);await ready()};
 const tab=name=>page.locator('.care-tabs').getByRole('tab',{name,exact:true}).click();
 const today=new Date().toLocaleDateString('en-CA');let next=new Date();do next.setDate(next.getDate()+1);while([0,6].includes(next.getDay()));const nextDay=next.toLocaleDateString('en-CA');
 const workspace=async(p,e)=>{await page.goto(base+'/care/patients/'+p+'/encounters/'+e);await page.waitForFunction(()=>window.Alpine);await page.waitForFunction(()=>Alpine.$data(document.body).v.id)};
 try{
  // 1 · Receptionist: register → book → check in → queue
  await login('Receptionist');
  for(const name of ['Dashboard','Patient Registration','Appointments','Check-In','Queue Management'])ok(await page.locator('.nav2 .nav-link',{hasText:name}).first().isVisible(),'Receptionist navigation: '+name);
  for(const name of ['Register patient','Book appointment','Check-in desk','Queue management'])ok(await page.locator('.page2-actions a',{hasText:name}).first().isVisible(),'Receptionist dashboard action: '+name);
  await Promise.all([page.waitForURL(u=>true),page.locator('.page2-actions a',{hasText:'Register patient'}).first().click()]);await ready();await page.waitForFunction(()=>Alpine.$data(document.body).modal==='form');
  const pname='Synthetic Journey '+Date.now();await page.locator('#field-name').fill(pname);await page.locator('#field-gender').selectOption('Female');await page.locator('#field-dateOfBirth').fill('1985-05-05');await page.locator('#field-phone').fill('00000001');
  await page.getByRole('button',{name:'Create record',exact:true}).click();await page.waitForFunction(()=>!Alpine.$data(document.body).modal);
  const patient=(await call('GET','/api/records/patients?search='+encodeURIComponent(pname))).items[0].id;ok(!!patient,'Receptionist registered patient');
  const slot=(await call('GET','/api/care/slots?doctor=DOC-0001&date='+today)).at(-1);
  const appointment=(await call('POST','/api/records/appointments',{name:'Journey consultation',patient,doctor:'DOC-0001',department:'DEP-0001',date:today,time:slot,type:'Consultation',reason:'Synthetic journey'})).id;
  await go('/appointments?stage=arrivalStatus%3DWaiting%20for%20Check-In');await page.waitForFunction(()=>Alpine.$data(document.body).records.length>=0&&!Alpine.$data(document.body).loading);
  ok((await state()).records.every(r=>r.arrivalStatus==='Waiting for Check-In'),'Check-In view lists only patients waiting for check-in');
  await page.goto(base+'/appointments/'+appointment);await page.locator('.detail-drawer').waitFor();await page.locator('.detail-actions').getByRole('button',{name:'Check In',exact:true}).click();
  await page.waitForURL(/\/care\/queue/);await page.waitForFunction(p=>Alpine.$data(document.body).data.queues?.some(q=>q.patient===p),patient);ok(true,'Check-in lands receptionist on queue with patient');
  const encounter=(await call('GET','/api/records/appointments/'+appointment)).record.encounter;ok(/^VIS-/.test(encounter),'Appointment linked to encounter');

  // 2 · Nurse: triage and vitals in the same encounter
  await login('Nurse');await workspace(patient,encounter);await tab('Triage & Vital Signs');
  await page.getByLabel('Chief complaint',{exact:false}).fill('Synthetic cough');await page.getByLabel('Blood pressure (mmHg)',{exact:false}).fill('118/76');await page.getByLabel('Temperature',{exact:false}).fill('37.1');await page.getByLabel('Heart rate',{exact:false}).fill('82');await page.getByLabel('Oxygen saturation',{exact:false}).fill('98');
  await page.locator('.clinical-form button').first().click();await page.waitForFunction(()=>Alpine.$data(document.body).data.documents.some(d=>d.kind==='triage'));ok(true,'Nurse recorded vitals');

  // 3 · Doctor: queue → consultation → notes → orders → certificate → follow-up
  await login('Doctor');for(const name of ['Dashboard','My Queue','My Appointments','My Patients','Consultations','Orders and Results','Medical Certificates','Tasks'])ok(await page.locator('.nav2 .nav-link',{hasText:name}).first().isVisible(),'Doctor navigation: '+name);
  await page.goto(base+'/care/queue');await page.waitForFunction(e=>Alpine.$data(document.body).data.queues?.some(q=>q.encounter===e),encounter);ok(true,'Patient in doctor queue');
  await workspace(patient,encounter);ok((await state()).vitals?.temperature==='37.1','Doctor sees nurse vitals');
  await page.getByRole('button',{name:'Start Consultation',exact:true}).click();await page.waitForFunction(()=>Alpine.$data(document.body).v.status==='In consultation');
  await tab('Consultation');await page.getByLabel('Subjective / history of present illness',{exact:false}).fill('Synthetic three-day cough.');await page.getByLabel('Assessment',{exact:false}).fill('Clinician-entered demo assessment');await page.getByLabel('Treatment and follow-up plan',{exact:false}).fill('Review in one week');
  await page.getByRole('button',{name:'Save Draft',exact:true}).click();await page.waitForFunction(()=>Alpine.$data(document.body).data.documents.some(d=>d.kind==='note'));await page.locator('.care-record').filter({hasText:'Draft'}).getByRole('button',{name:'Review / Finalize',exact:true}).click();await page.waitForFunction(()=>Alpine.$data(document.body).data.documents.some(d=>d.kind==='note'&&d.status==='Signed'));ok(true,'Note signed');
  await tab('Laboratory Orders');await page.getByLabel('Laboratory test',{exact:false}).selectOption('TST-0001');await page.getByLabel('Clinical indication',{exact:false}).fill('Synthetic indication');await page.locator('.clinical-form button').first().click();await page.waitForFunction(()=>Alpine.$data(document.body).data.current.laboratory.length===1);
  const lab=(await state()).data.current.laboratory[0].id;ok(true,'Lab order created');
  await tab('Imaging Orders');await page.getByLabel('Examination / procedure',{exact:false}).fill('Chest X-ray');await page.getByLabel('Modality',{exact:false}).selectOption('X-ray');await page.getByLabel('Clinical indication',{exact:false}).fill('Synthetic imaging indication');await page.locator('.clinical-form button').first().click();await page.waitForFunction(()=>Alpine.$data(document.body).data.current.imaging.length===1);
  await page.locator('.care-record').filter({hasText:'Chest X-ray'}).getByRole('button',{name:'Cancel Order',exact:true}).click();await page.waitForFunction(()=>Alpine.$data(document.body).data.current.imaging[0].status==='Cancelled');ok(true,'Doctor cancelled unneeded imaging order with reason');
  await tab('Medical Certificates');await page.getByLabel('Certificate template',{exact:false}).selectOption('Medical Leave Certificate');await page.getByLabel('Purpose',{exact:false}).fill('Employer');await page.getByLabel('Clinician-reviewed statement',{exact:false}).fill('Synthetic statement written by the clinician.');await page.getByLabel('Leave start',{exact:false}).fill(today);await page.getByLabel('Leave end',{exact:false}).fill(nextDay);
  await page.getByRole('button',{name:'Save Draft',exact:true}).click();await page.waitForFunction(()=>Alpine.$data(document.body).data.documents.some(d=>d.kind==='certificate'));await page.locator('.care-record').filter({hasText:'Draft'}).getByRole('button',{name:'Review / Finalize',exact:true}).click();await page.waitForFunction(()=>Alpine.$data(document.body).data.documents.some(d=>d.kind==='certificate'&&d.status==='Issued'));ok(true,'Certificate issued');
  await tab('Follow-Up & Discharge');await page.getByLabel('Follow-up date',{exact:false}).fill(nextDay);await page.getByLabel('Time',{exact:false}).fill('10:15');await page.getByLabel('Reason for visit',{exact:false}).fill('Synthetic review visit');await page.getByLabel('Discharge plan',{exact:false}).fill('Synthetic discharge plan');
  await page.locator('.clinical-form button').first().click();await page.waitForFunction(()=>Alpine.$data(document.body).data.history.appointments.some(a=>a.type==='Follow-up'));ok(true,'Follow-up appointment booked from workspace');
  const blocked=await page.evaluate(async()=>{const d=Alpine.$data(document.body);await d.act('complete',{plan:'x'});return d.error});ok(/Results are pending/.test(blocked),'Discharge blocked while lab result pending');

  // 4 · Laboratory: worklist → collect → result → review → approve
  await login('Laboratory Staff');await quick('Sample Collection');await page.waitForFunction(id=>!Alpine.$data(document.body).loading&&Alpine.$data(document.body).records.some(r=>r.id===id),lab);ok((await state()).records.every(r=>r.status==='Ordered'),'Lab worklist filtered to samples to collect');
  for(const action of ['Collect sample','Start test'])await call('POST','/api/records/laboratory/'+lab+'/actions',{action});
  const row=(await call('GET','/api/records/laboratory/'+lab)).record;await call('PUT','/api/records/laboratory/'+lab,{...row,result:'Synthetic value 5.1'});
  for(const action of ['Enter result','Review','Approve'])await call('POST','/api/records/laboratory/'+lab+'/actions',{action});ok((await call('GET','/api/records/laboratory/'+lab)).record.status==='Approved','Lab result approved');

  // 5 · Doctor: review → release → complete → charges
  await login('Doctor');await workspace(patient,encounter);await tab('Laboratory Orders');
  await page.getByRole('button',{name:'Doctor Review',exact:true}).click();await page.waitForFunction(()=>Alpine.$data(document.body).data.current.laboratory[0].reviewedByDoctor==='true');
  await page.getByRole('button',{name:'Release to patient',exact:true}).click();await page.waitForFunction(()=>Alpine.$data(document.body).data.current.laboratory[0].released==='true');ok(true,'Result reviewed and released');
  await tab('Follow-Up & Discharge');await page.evaluate(()=>{Alpine.$data(document.body).form.dischargePlan='Synthetic discharge plan'});await page.getByRole('button',{name:'Complete Consultation',exact:true}).click();
  await page.waitForFunction(()=>Alpine.$data(document.body).v.status==='Completed');ok(true,'Consultation completed');
  await page.locator('.care-charges .care-record').first().waitFor();ok(await page.locator('.care-charges').getByText('Draft').first().isVisible(),'Draft encounter invoice visible in workspace');

  // 6 · Cashier sees the draft
  await login('Cashier');await go('/billing?status=Draft');await page.waitForFunction(()=>!Alpine.$data(document.body).loading);
  ok((await call('GET','/api/records/billing?status=Draft&size=100')).items.some(b=>b.encounter===encounter),'Cashier can find encounter invoice');

  // 7 · Data consistency: every record references the same patient and encounter
  await login('Administrator');const ctx=await call('GET','/api/care/context?patient='+patient+'&encounter='+encounter);
  const linked=[...Object.values(ctx.current).flat(),...ctx.documents.filter(d=>d.encounter)];ok(linked.length>=8,'Encounter has linked records ('+linked.length+')');
  ok(linked.every(r=>r.patient===patient),'All linked records reference the same patient');ok(Object.entries(ctx.current).every(([m,rows])=>rows.every(r=>m==='visits'?r.id===encounter:r.encounter===encounter)),'All encounter records reference the same encounter');
  ok((await call('GET','/api/records/appointments/'+appointment)).record.status==='Completed','Appointment completed with encounter');
  ok(ctx.history.appointments.some(a=>a.type==='Follow-up'&&a.previousEncounter===encounter),'Follow-up references originating encounter');

  // 8 · Queue transfer from the generic queue screen keeps the patient queued
  const p2=(await call('POST','/api/records/patients',{name:'Synthetic Transfer '+Date.now(),gender:'Male',dateOfBirth:'1979-03-03',phone:'00000002'})).id;
  const a2=(await call('POST','/api/records/appointments',{name:'Transfer visit',patient:p2,doctor:'DOC-0001',department:'DEP-0001',date:today,time:(await call('GET','/api/care/slots?doctor=DOC-0001&date='+today)).at(-1),type:'Consultation'})).id;
  const e2=(await call('POST','/api/care/action',{action:'checkin',patient:p2,appointment:a2})).id;const q2=(await call('GET','/api/care/context?patient='+p2+'&encounter='+e2)).current.queues[0].id;
  await page.goto(base+'/queues/'+q2);await page.locator('.detail-drawer').waitFor();await page.locator('.detail-drawer').getByRole('button',{name:'Transfer',exact:true}).click();
  await page.locator('[x-model="actionForm.target"]').selectOption('DEP-0002');await page.locator('[x-model="actionForm.doctor"]').selectOption('DOC-0002');await page.getByRole('button',{name:'Confirm action',exact:true}).click();await page.waitForFunction(()=>!Alpine.$data(document.body).modal);
  const after=(await call('GET','/api/care/context?patient='+p2+'&encounter='+e2)).current.queues;ok(after.some(q=>q.id===q2&&q.status==='Transferred')&&after.some(q=>q.status==='Waiting'&&q.doctor==='DOC-0002'),'Transfer dialog moves patient to receiving doctor queue');

  // 9 · Khmer, dark mode and mobile sweeps
  const pages=[['dashboard','/'],['appointments','/appointments'],['queues','/queues'],['laboratory','/laboratory'],['care-queue','/care/queue'],['workspace','/care/patients/'+patient+'/encounters/'+encounter],['mini-doctor','/mini/doctor'],['appointment-workspace','/care/appointments/'+appointment],['checkin-desk','/care/checkin'],['telegram-settings','/settings/telegram'],['overview','/overview'],['certificates','/care/certificates'],['tasks','/care/tasks']];
  const sweep=async key=>{await page.waitForTimeout(700);results.untranslated[key]=await page.evaluate(()=>{const out=new Set();const w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let n;while(n=w.nextNode()){const t=n.nodeValue.trim();const el=n.parentElement;if(!t||!el||!el.offsetParent||el.closest('[data-no-translate],script,style,pre,input,textarea,select'))continue;if(/[A-Za-z]{3,}/.test(t)&&!/^(?:[A-Z]{2,5}-[0-9A-Z-]+|Q-\d+|careplus|CarePlus.*|DEMO.*|Dr\. .*|mmHg|bpm)$/i.test(t))out.add(t.slice(0,80))}return [...out].slice(0,60)});
   results.lightSurfacesInDark[key]=await page.evaluate(()=>{if(!document.body.classList.contains('theme-dark'))return [];const lum=c=>{const m=c.match(/[\d.]+/g);if(!m||m.length<3||(m[3]!==undefined&&+m[3]<.5))return 0;const [r,g,b]=m.map(Number);return (0.2126*r+0.7152*g+0.0722*b)/255};return [...document.querySelectorAll('body *')].filter(e=>{const r=e.getBoundingClientRect();return e.offsetParent&&!e.closest('.qr-box')&&r.width*r.height>2500&&lum(getComputedStyle(e).backgroundColor)>0.85}).slice(0,15).map(e=>e.tagName.toLowerCase()+'.'+[...e.classList].join('.'))})};
  await page.goto(base+'/');await page.locator('.pref-desktop select').first().selectOption('km');await page.locator('.pref-desktop select').last().selectOption('dark');
  for(const [key,url] of pages){await page.goto(base+url);await page.waitForLoadState('networkidle');ok(await page.evaluate(()=>document.documentElement.lang==='km'&&document.body.classList.contains('theme-dark')),'Khmer + dark persisted on '+key);await sweep(key);await page.screenshot({path:'test-results/journey-km-dark-'+key+'.png'})}
  await page.setViewportSize({width:390,height:844});
  for(const [key,url] of pages){await page.goto(base+url);await page.waitForLoadState('networkidle');await page.waitForTimeout(400);results.overflow[key]=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);await page.screenshot({path:'test-results/journey-mobile-'+key+'.png'})}
  await login('Patient','PAT-0018');await page.waitForFunction(()=>Alpine.$data(document.body).data.patient?.id);await sweep('mini-patient');results.overflow['mini-patient']=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
  await page.locator('.mini-nav').getByRole('button',{name:/Records|កំណត់ត្រា/}).first().click().catch(()=>{});await page.screenshot({path:'test-results/journey-mobile-mini-patient.png'});
  for(const [k,v] of Object.entries(results.overflow))ok(v<=1,'No horizontal overflow at 390px: '+k+' ('+v+'px)');
  ok(results.errors.length===0,'No browser errors');
 }catch(e){results.failure=String(e.stack||e);await page.screenshot({path:'test-results/journey-failure.png',fullPage:true});process.exitCode=1}
 finally{fs.mkdirSync('test-results',{recursive:true});fs.writeFileSync('test-results/journey-report.json',JSON.stringify(results,null,2));console.log(JSON.stringify({passed:results.assertions.length,failure:results.failure,errors:results.errors,overflow:results.overflow},null,1));await browser.close()}
})();
