// QR + Mini App end to end: arrival-poster self check-in, reception ticket scan (manual code path), replay protection,
// doctor Mini App call → patient sees it, desktop/mobile consistency, Telegram settings deep links, layouts.
// Usage: start the app (fresh), then `node scripts/qr-mini-test.cjs`.
const {chromium}=require('playwright');const fs=require('fs'),assert=require('assert');
const base=process.env.HOSPITAL_URL||'http://127.0.0.1:8090';const results={assertions:[],errors:[],overflow:{}};
const ok=(v,m)=>{assert(v,m);results.assertions.push(m)};
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE||undefined});
 const open=async(vp)=>{const c=await browser.newContext({viewport:vp||{width:1440,height:1000}});await c.route('https://**/*',r=>r.abort());const p=await c.newPage();p.on('pageerror',e=>results.errors.push(e.message));p.on('dialog',d=>d.accept());return p};
 const login=async(p,role,identity,next)=>{await p.goto(base+'/login'+(next?'?next='+encodeURIComponent(next):''));await p.locator('select[name=role]').selectOption(role);if(identity)await p.locator('select[name=patientIdentity]').selectOption(identity);await Promise.all([p.waitForNavigation(),p.locator('form[action="/login"] button[type=submit]').click()]);await p.waitForLoadState('networkidle')};
 const api=(p,url,body)=>p.evaluate(async([u,b])=>{const r=await fetch(u,{method:b?'POST':'GET',headers:b?{'Content-Type':'application/json','X-CSRF-TOKEN':document.querySelector('meta[name=_csrf]').content}:{},body:b?JSON.stringify(b):undefined});const t=await r.text();if(!r.ok)throw Error(u+' → '+r.status+' '+t);return JSON.parse(t)},[url,body]);
 const state=p=>p.evaluate(()=>Alpine.$data(document.body));
 const overflow=async(p,key)=>{results.overflow[key]=await p.evaluate(()=>document.documentElement.scrollWidth-innerWidth);ok(results.overflow[key]<=1,'No horizontal overflow: '+key)};
 const today=new Date().toLocaleDateString('en-CA');
 try{
  // Setup: two appointments today for the two demo patient identities.
  const admin=await open();await login(admin,'Administrator');
  const free=async()=>(await api(admin,'/api/care/slots?doctor=DOC-0001&date='+today));
  let slots=await free();const a18=(await api(admin,'/api/records/appointments',{name:'QR arrival visit',patient:'PAT-0018',doctor:'DOC-0001',department:'DEP-0001',date:today,time:slots.at(-1),type:'Consultation',reason:'Synthetic QR arrival'})).id;
  // PAT-0001 has a seeded active visit today; the phone must refuse a ticket until it is closed.
  slots=await free();const a01=(await api(admin,'/api/records/appointments',{name:'QR ticket visit',patient:'PAT-0001',doctor:'DOC-0001',department:'DEP-0001',date:today,time:slots.at(-2),type:'Consultation',reason:'Synthetic QR ticket'})).id;

  // 1 · Reception desk shows today's arrival QR
  const desk=await open();await login(desk,'Receptionist');await desk.goto(base+'/care/checkin');await desk.waitForFunction(()=>Alpine.$data(document.body).arrival?.code);
  const code=(await state(desk)).arrival.code;ok(/^[A-Z2-9]{8}$/.test(code),'Arrival code shown at reception');
  ok(await desk.locator('.qr-box svg path').first().isVisible(),'Arrival QR rendered');ok((await desk.locator('.qr-box svg').first().boundingBox()).width>150,'Arrival QR is large enough to scan');
  const arrivalLink=(await state(desk)).arrivalLink;ok(arrivalLink.includes('startapp=arrive-'+code),'Arrival QR encodes a Mini App start parameter, no patient data');
  await overflow(desk,'checkin-desktop');await desk.screenshot({path:'test-results/qr-checkin-desk.png',fullPage:true});

  // 2 · Patient scans the poster: /tg (demo) → sign in → Mini App with arrival code → confirm arrival
  const phone=await open({width:390,height:844});await phone.goto(arrivalLink);await phone.locator('h1:visible').first().waitFor();
  ok(/not connected/i.test(await phone.locator('h1:visible').first().textContent()),'Demo mode explains Telegram is not connected');
  await phone.getByRole('link',{name:'Open the demo preview'}).click();await phone.waitForURL(/\/login\?next=/);ok(true,'Unsigned user is sent to sign-in with return path');
  await phone.locator('select[name=role]').selectOption('Patient');await phone.locator('select[name=patientIdentity]').selectOption('PAT-0018');await Promise.all([phone.waitForNavigation(),phone.locator('form[action="/login"] button[type=submit]').click()]);
  await phone.waitForURL(/\/mini\/patient\?arrival=/);await phone.waitForFunction(()=>Alpine.$data(document.body).checkin.open);
  ok((await state(phone)).checkin.code===code,'Check-in sheet opens with the scanned arrival code');
  await phone.getByText('I confirm I am the patient for this appointment.').click();await phone.getByRole('button',{name:'Confirm arrival',exact:true}).click();
  await phone.waitForFunction(()=>Alpine.$data(document.body).tab==='Queue'&&Alpine.$data(document.body).activeQueue);const ticket=(await state(phone)).activeQueue.name;
  ok(/^Q-\d+/.test(ticket),'Patient receives a queue ticket after arrival check-in');await phone.screenshot({path:'test-results/qr-patient-queue.png'});await overflow(phone,'mini-patient-queue');

  // 3 · Desktop shows the same state
  const doc=await open();await login(doc,'Doctor');await doc.goto(base+'/care/appointments/'+a18);await doc.locator('.pj-step').first().waitFor();
  const j=await state(doc);ok(j.q.name===ticket,'Appointment workspace shows the same queue ticket as the phone');ok(j.j.checkIns[0].method.includes('arrival QR'),'Check-in history records the arrival QR channel');
  ok(j.j.steps.find(s=>s.key==='checkin').status==='done','Journey marks check-in done');await doc.screenshot({path:'test-results/qr-appointment-workspace.png',fullPage:true});

  // 4 · Second patient shows ticket QR; reception redeems it once
  const p01=await open({width:390,height:844});await login(p01,'Patient','PAT-0001');
  const blocked=await p01.evaluate(async id=>{const r=await fetch('/api/care/checkin',{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-TOKEN':document.querySelector('meta[name=_csrf]').content},body:JSON.stringify({appointment:id,verified:'true'})});return (await r.json()).message},a01);
  ok(/already have an active visit/.test(blocked),'Phone refuses a ticket while another visit is active');
  for(const v of (await api(admin,'/api/records/visits?filter=patient=PAT-0001&status=Waiting&size=20')).items)await api(admin,'/api/records/visits/'+v.id+'/actions',{action:'Cancel'});
await p01.goto(base+'/mini/patient');await p01.waitForFunction(()=>Alpine.$data(document.body).data.patient?.id);
  await p01.locator('.mini-tabbar').getByRole('button',{name:'Appointments'}).click();await p01.locator('article.mini-card').filter({hasText:'Synthetic QR ticket'}).first().waitFor().catch(()=>{});
  await p01.evaluate(id=>{const d=Alpine.$data(document.body);d.openCheckin(d.data.appointments.find(a=>a.id===id))},a01);await p01.getByRole('button',{name:'Show QR to reception',exact:true}).click();
  await p01.waitForFunction(()=>Alpine.$data(document.body).checkin.token);const token=(await state(p01)).checkin.token;ok(await p01.locator('.qr-box svg path').count()===1,'Patient ticket QR rendered');await p01.screenshot({path:'test-results/qr-patient-ticket.png'});
  await desk.locator('#manual-code').fill('CPCI:'+token);await desk.getByRole('button',{name:'Check in',exact:true}).first().click();await desk.waitForFunction(()=>Alpine.$data(document.body).result?.queueNumber);
  ok((await state(desk)).result.patient==='PAT-0001','Reception redeemed the patient ticket');
  await desk.locator('#manual-code').fill('CPCI:'+token);await desk.getByRole('button',{name:'Check in',exact:true}).first().click();await desk.waitForFunction(()=>Alpine.$data(document.body).error);
  ok(/invalid or expired/.test((await state(desk)).error),'Replayed ticket is rejected');
  await p01.waitForFunction(()=>!Alpine.$data(document.body).checkin.open,null,{timeout:8000});ok((await state(p01)).tab==='Queue','Patient phone switches to the queue after reception scans the ticket');

  // 5 · Doctor Mini App calls the arrival patient; patient sees the call
  const dm=await open({width:390,height:844});await login(dm,'Doctor');await dm.goto(base+'/mini/doctor?tab=Queue');await dm.waitForFunction(()=>Alpine.$data(document.body).docQueue.length);
  const row=dm.locator('article.mini-card').filter({hasText:ticket});await row.getByRole('button',{name:'Call',exact:true}).click();await dm.waitForFunction(t=>Alpine.$data(document.body).docQueue.find(q=>q.name===t)?.status==='Called',ticket);ok(true,'Doctor called the patient from the Mini App');
  await dm.screenshot({path:'test-results/qr-doctor-queue.png'});await overflow(dm,'mini-doctor-queue');
  await phone.evaluate(()=>Alpine.$data(document.body).load());await phone.waitForFunction(()=>Alpine.$data(document.body).activeQueue?.status==='Called');ok(await phone.getByText("It's your turn.").isVisible(),'Patient phone shows the call');

  // 6 · Telegram settings: validation and deep links
  await admin.goto(base+'/settings/telegram');await admin.waitForFunction(()=>Alpine.$data(document.body).s.mode);ok(await admin.locator('.qr-box svg').count()===3,'Doctor, patient and arrival QR codes rendered');
  await admin.locator('#tg-bot').fill('not a bot');await admin.getByRole('button',{name:'Save configuration'}).click();await admin.waitForFunction(()=>Alpine.$data(document.body).error);ok(/must end in/.test((await state(admin)).error),'Invalid bot username rejected');
  await admin.locator('#tg-bot').fill('CarePlusDemoBot');await admin.locator('#tg-url').fill('https://care.example.test');await admin.getByRole('button',{name:'Save configuration'}).click();await admin.waitForFunction(()=>Alpine.$data(document.body).links.kind==='telegram');
  ok((await state(admin)).links.patient==='https://t.me/CarePlusDemoBot?startapp=patient','Patient QR encodes the Telegram deep link');ok((await state(admin)).links.doctor==='https://t.me/CarePlusDemoBot?startapp=doctor','Doctor QR encodes the Telegram deep link');
  ok(!(await admin.content()).includes('bot-token'),'No token material in the page');await admin.screenshot({path:'test-results/qr-telegram-settings.png',fullPage:true});

  // 7 · Responsive checks
  for(const [w,h] of [[768,1024],[390,844]]){await doc.setViewportSize({width:w,height:h});for(const [k,u] of [['appointment','/care/appointments/'+a18],['queue','/care/queue']]){await doc.goto(base+u);await doc.waitForLoadState('networkidle');await doc.waitForTimeout(500);await overflow(doc,k+'-'+w);await doc.screenshot({path:`test-results/qr-${k}-${w}.png`,fullPage:true})}}
  ok(results.errors.length===0,'No browser errors');
 }catch(e){results.failure=String(e.stack||e);process.exitCode=1}
 finally{fs.mkdirSync('test-results',{recursive:true});fs.writeFileSync('test-results/qr-mini-report.json',JSON.stringify(results,null,2));console.log(JSON.stringify({passed:results.assertions.length,failure:results.failure,errors:results.errors},null,1));await browser.close()}
})();
