// Capture the actual checkout before/after this requirements upgrade.
const {chromium}=require('playwright');
const fs=require('fs'),assert=require('node:assert/strict');
const base=process.env.HOSPITAL_URL||'http://127.0.0.1:8090';
const stage=process.env.UI_STAGE||'after';
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE||undefined});
 const context=await browser.newContext({viewport:{width:1440,height:1000}});
 const page=await context.newPage(),errors=[],failures=[],checks=[];
 page.on('pageerror',e=>errors.push(e.message));
 page.on('response',r=>{if(r.status()>=400&&r.url().startsWith(base))failures.push(r.status()+' '+r.url())});
 await context.route('https://**/*',r=>r.abort());
 const dir='docs/screenshots/requirements-'+stage;fs.mkdirSync(dir,{recursive:true});
 const login=async role=>{await page.goto(base+'/login');await page.locator('select[name=role]').selectOption(role);await page.locator('form[action="/login"] button[type=submit]').click();await page.waitForLoadState('networkidle')};
 try{
  for(const [label,width,lang,theme] of [['desktop',1440,'en','light'],['tablet',768,'km','dark'],['mobile',390,'km','dark'],['phone',360,'en','light']]){
   await page.setViewportSize({width,height:1000});await login('Administrator');
   await page.evaluate(({lang,theme})=>{localStorage.setItem('careplus-language',lang);localStorage.setItem('careplus-theme',theme)}, {lang,theme});
   // Use the app's controls so preference persistence follows its current contract.
   await page.setViewportSize({width:1440,height:1000});await page.goto(base+'/');
   await page.locator('.pref-desktop select').first().selectOption(lang);await page.locator('.pref-desktop select').last().selectOption(theme);
   await page.setViewportSize({width,height:1000});
   const appointments=(await (await context.request.get(base+'/api/records/appointments?size=100')).json()).items;
   const visits=(await (await context.request.get(base+'/api/records/visits?size=100')).json()).items;
   const visit=visits.find(v=>v.patient&&v.id);
   const routes=[['dashboard','/'],['appointment','/care/appointments/'+appointments[0].id],['workspace','/care/patients/'+visit.patient+'/encounters/'+visit.id],['queue','/care/queue'],['telegram','/settings/telegram'],['doctor-mini','/mini/doctor']];
   if(stage!=='before')routes.push(['schedules','/care/schedules']);
   for(const [key,url] of routes){
    await page.goto(base+url);await page.waitForLoadState('networkidle');await page.waitForTimeout(300);
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+2);
    checks.push({page:key,label,width,lang,theme,overflow});
    await page.screenshot({path:dir+'/'+label+'-'+key+'.png',fullPage:true});
   }
   await login('Patient');await page.goto(base+'/mini/patient');await page.waitForLoadState('networkidle');
   checks.push({page:'patient-mini',label,width,lang,theme,overflow:await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+2)});
   await page.screenshot({path:dir+'/'+label+'-patient-mini.png',fullPage:true});
  }
  if(stage!=='before'){
   await page.setViewportSize({width:1440,height:1000});await login('Administrator');
   await page.evaluate(()=>localStorage.setItem('careplus-language','en'));await page.goto(base+'/settings/telegram');await page.waitForLoadState('networkidle');
   await page.locator('#bot-language').selectOption('km');await page.getByRole('button',{name:'Preview bot reply',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.bot-reply')?.textContent.includes('សូមស្វាគមន៍'));
   await page.goto(base+'/care/schedules');await page.waitForLoadState('networkidle');
   const date=new Date();date.setDate(date.getDate()+45);await page.locator('#schedule-date').fill(date.toLocaleDateString('en-CA'));await page.locator('#schedule-date').blur();await page.waitForLoadState('networkidle');
   await page.locator('#schedule-start').fill('10:00');await page.locator('#schedule-end').fill('12:00');await page.locator('#schedule-break-start').fill('');await page.locator('#schedule-break-end').fill('');await page.locator('#schedule-interval').fill('30');await page.locator('#schedule-location').fill('Synthetic schedule QA');
   await page.evaluate(()=>Alpine.$data(document.body).closed=false);await page.getByRole('button',{name:'Save schedule',exact:true}).click();await page.waitForFunction(()=>Alpine.$data(document.body).message==='Doctor schedule saved.');
   assert.equal(await page.locator('.schedule-slots .badge2').count(),4);
   for(const role of ['Hospital Director','Imaging Staff']){await login(role);await page.screenshot({path:dir+'/'+role.replaceAll(' ','-')+'.png',fullPage:true})}
   await page.goto(base+'/care/queue');await page.waitForLoadState('networkidle');assert.equal(await page.evaluate(()=>Alpine.$data(document.body).station),'Imaging');
   await login('Hospital Director');await page.goto(base+'/staff');await page.waitForLoadState('networkidle');assert.equal(await page.getByRole('button',{name:'New staff member',exact:true}).isVisible().catch(()=>false),false);
  }
  fs.writeFileSync(dir+'/results.json',JSON.stringify({checks,errors,failures},null,2));
  assert.deepEqual(errors,[],'Browser JavaScript errors');assert.deepEqual(failures,[],'Failed application requests');
  assert.deepEqual(checks.filter(c=>c.overflow),[],'Horizontal overflow');
  console.log(JSON.stringify({captures:checks.length,errors,failures}));
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
