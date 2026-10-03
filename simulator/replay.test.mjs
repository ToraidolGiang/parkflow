import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { eventsFromCsv,replay } from './replay.mjs';
const csv='ts_in,ts_out,gate_id,card_id,lot_id,vehicle_type\n2026-09-22 07:00:00,2026-09-22 08:00:00,G1,'+'a'.repeat(64)+',A,Xe máy\n';
test('conversion, timezone, missing exits and stable IDs',()=>{
  const events=eventsFromCsv(csv,'2026-09-22');
  assert.equal(events.length,2); assert.equal(events[0].ts,'2026-09-22T07:00:00+07:00');
  assert.equal(events[1].direction,'out'); assert.deepEqual(events,eventsFromCsv(csv,'2026-09-22'));
  assert.equal(eventsFromCsv(csv.replace('2026-09-22 08:00:00',''),'2026-09-22').length,1);
  assert.throws(()=>eventsFromCsv(csv.replace(',A,',',Z,'),'2026-09-22'));
  assert.throws(()=>eventsFromCsv(csv.replace(',A,',',D,'),'2026-09-22'));
  assert.throws(()=>eventsFromCsv(csv.replace(',A,',',C,'),'2026-09-22'));
  for(const lot of ['C1','C2']) assert.equal(eventsFromCsv(csv.replace(',A,',`,`+lot+`,`),'2026-09-22')[0].lot_id,lot);
});
test('HTTP delivery, retry and duplicate acknowledgement',async()=>{
  const ids=new Set(); let requests=0;
  const server=createServer(async(req,res)=>{
    assert.equal(req.url,'/api/events'); assert.equal(req.headers['x-api-key'],'test-only');
    let body=''; for await(const chunk of req) body+=chunk;
    const event=JSON.parse(body); const duplicate=ids.has(event.event_id); ids.add(event.event_id);
    if(++requests===1) {res.writeHead(503);res.end();return;}
    res.setHeader('Content-Type','application/json');res.end(JSON.stringify({accepted:duplicate?0:1,duplicate:duplicate?1:0,rejected:[]}));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try {
    const events=eventsFromCsv(csv,'2026-09-22');
    const options={url:`http://127.0.0.1:${server.address().port}/api/events`,key:'test-only',speed:1e9,wait:async()=>{}};
    assert.deepEqual(await replay(events,options),{sent:2,accepted:1,duplicate:1});
    assert.deepEqual(await replay(events,options),{sent:2,accepted:0,duplicate:2});
    await assert.rejects(replay(events,{...options,speed:0}));
    await assert.rejects(replay(events,{...options,fetchFn:async()=>({ok:true,status:200,json:async()=>({accepted:0,duplicate:0,rejected:[{reason:'UNKNOWN_LOT'}]})})}));
  } finally {await new Promise(resolve=>server.close(resolve));}
});
