import { readFileSync } from 'node:fs';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { setTimeout as sleep } from 'node:timers/promises';
import { createHash } from 'node:crypto';

const campus=JSON.parse(readFileSync(new URL('./data/campus_config.json',import.meta.url),'utf8'));
export function eventsFromCsv(text, date) {
  const lines=text.trim().split(/\r?\n/);
  if(lines.shift()!=='ts_in,ts_out,gate_id,card_id,lot_id,vehicle_type') throw new Error('Unexpected CSV header');
  const events=[];
  lines.forEach((line,index)=>{
    const [tsIn,tsOut,gate,card,lot,vehicle,...extra]=line.split(',');
    if(extra.length||!campus.lots.some(l=>l.id===lot)||!campus.gates.some(g=>g.code===gate)||!/^[a-f0-9]{64}$/.test(card)) throw new Error(`Invalid row ${index+2}`);
    const type={'Xe máy':'motorbike','Xe đạp':'bicycle','Ô tô':'car'}[vehicle];
    if(!type||!tsIn|| (tsOut && tsOut<tsIn)) throw new Error(`Invalid session ${index+2}`);
    for(const [direction,ts] of [['in',tsIn],['out',tsOut]]) {
      if(!ts) continue;
      if(!/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(ts)||!Number.isFinite(Date.parse(ts.replace(' ','T')+'+07:00'))) throw new Error(`Invalid timestamp ${index+2}`);
      if(!ts.startsWith(date+' ')) continue;
      events.push({event_id:'evt_'+createHash('sha256').update(`${ts}|${lot}|${card}|${direction}`).digest('hex').slice(0,40),ts:ts.replace(' ','T')+'+07:00',lot_id:lot,direction,vehicle_type:type,card_hash:card});
    }
  });
  return events.sort((a,b)=>a.ts.localeCompare(b.ts)||a.event_id.localeCompare(b.event_id));
}

export async function replay(events,{url,key,speed=30,fetchFn=fetch,wait=sleep,onProgress=()=>{}}) {
  if(!Number.isFinite(speed)||speed<=0) throw new Error('Speed must be positive');
  if(!key) throw new Error('Set EVENTS_API_KEY before replay');
  const started=performance.now(), origin=Date.parse(events[0]?.ts);
  let accepted=0,duplicate=0;
  for(const event of events) {
    const delay=(Date.parse(event.ts)-origin)/speed-(performance.now()-started);
    if(delay>0) await wait(delay);
    let result;
    for(let attempt=0;attempt<3;attempt++) {
      let response;
      try { response=await fetchFn(url,{method:'POST',headers:{'Content-Type':'application/json','X-Api-Key':key},body:JSON.stringify(event),signal:AbortSignal.timeout(10000)}); }
      catch(error) { if(attempt===2) throw error; await wait(250*(2**attempt)); continue; }
      if(response.status>=500||response.status===429) { if(attempt===2) throw new Error(`HTTP ${response.status}`); await wait(250*(2**attempt)); continue; }
      if(!response.ok) throw new Error(`HTTP ${response.status}: ${event.event_id}`);
      result=await response.json(); break;
    }
    if(!result||!Array.isArray(result.rejected)||result.rejected.length||!Number.isInteger(result.accepted)||!Number.isInteger(result.duplicate)||result.accepted<0||result.duplicate<0||result.accepted+result.duplicate!==1) throw new Error(`Event not acknowledged: ${event.event_id}`);
    accepted+=result.accepted; duplicate+=result.duplicate;
    onProgress({sent:accepted+duplicate,total:events.length,accepted,duplicate,simTime:event.ts});
  }
  return {sent:events.length,accepted,duplicate};
}

async function main() {
  const {values}=parseArgs({options:{file:{type:'string',default:fileURLToPath(new URL('./data/demo_log.csv',import.meta.url))},date:{type:'string',default:'2026-09-22'},url:{type:'string',default:'http://localhost:8080/api/events'},speed:{type:'string',default:'30'},'dry-run':{type:'boolean',default:false}}});
  if(!/^\d{4}-\d{2}-\d{2}$/.test(values.date)) throw new Error('Date must be YYYY-MM-DD');
  const events=eventsFromCsv(readFileSync(values.file,'utf8'),values.date);
  if(!events.length) throw new Error(`No events for ${values.date}`);
  if(values['dry-run']) console.log(JSON.stringify({events:events.length,first:events[0],last:events.at(-1)},null,2));
  else {
    console.error(`Replay ${values.date}: ${events.length} events, speed x${values.speed}`);
    console.log(await replay(events,{url:values.url,key:process.env.EVENTS_API_KEY,speed:Number(values.speed),onProgress:p=>{
      if(p.sent===1||p.sent%250===0||p.sent===p.total) console.error(`${p.sent}/${p.total} | accepted=${p.accepted} duplicate=${p.duplicate} | ${p.simTime}`);
    }}));
  }
}
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) main().catch(e=>{console.error(e.message);process.exitCode=1;});
