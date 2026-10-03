import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const run=promisify(execFile);
test('CLI delivers the whole demo log over HTTP; second replay is entirely duplicate', {timeout:240000}, async()=>{
  const rows=readFileSync(new URL('./data/demo_log.csv',import.meta.url),'utf8').trim().split('\n').slice(1).map(r=>r.split(','));
  const expected=rows.length+rows.filter(r=>r[1]).length;
  const expectedBalance=Object.fromEntries(['A','B','C1','C2'].map(l=>[l,rows.filter(r=>r[4]===l&&!r[1]).length]));
  const key='local-integration-test-only';
  const ids=new Map(),balance={A:0,B:0,C1:0,C2:0};
  let requests=0,lastTs='',failure;
  const server=createServer(async(req,res)=>{
    try {
      assert.equal(req.method,'POST');assert.equal(req.url,'/api/events');
      assert.equal(req.headers['x-api-key'],key);
      assert.equal(req.headers['content-type'],'application/json');
      let body='';for await(const chunk of req) body+=chunk;
      const e=JSON.parse(body);
      assert.ok(Object.hasOwn(balance,e.lot_id));
      assert.match(e.ts,/\+07:00$/);assert.ok(e.ts>=lastTs);lastTs=e.ts;
      assert.match(e.card_hash,/^[a-f0-9]{64}$/);
      assert.ok(['motorbike','bicycle','car'].includes(e.vehicle_type));
      assert.ok(['in','out'].includes(e.direction));
      const duplicate=ids.has(e.event_id);
      if(duplicate) assert.deepEqual(e,ids.get(e.event_id));
      else {ids.set(e.event_id,e);balance[e.lot_id]+=e.direction==='in'?1:-1;assert.ok(balance[e.lot_id]>=0);}
      requests++;
      res.setHeader('Content-Type','application/json');
      res.end(JSON.stringify({accepted:duplicate?0:1,duplicate:duplicate?1:0,rejected:[]}));
    } catch(error) {failure=error;res.writeHead(400);res.end();}
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try {
    const args=[fileURLToPath(new URL('./replay.mjs',import.meta.url)),'--url',`http://127.0.0.1:${server.address().port}/api/events`,'--speed','1000000000'];
    const options={env:{...process.env,EVENTS_API_KEY:key},timeout:110000};
    const first=await run(process.execPath,args,options);
    assert.ifError(failure);assert.equal(ids.size,expected);assert.equal(requests,expected);
    assert.deepEqual(balance,expectedBalance);
    assert.match(first.stdout,new RegExp(`accepted: ${expected}`));
    lastTs='';
    const second=await run(process.execPath,args,options);
    assert.ifError(failure);assert.equal(requests,expected*2);assert.equal(ids.size,expected);
    assert.deepEqual(balance,expectedBalance);
    assert.match(second.stdout,new RegExp(`duplicate: ${expected}`));
    assert.ok(second.stderr.includes(`${expected}/${expected}`));
    console.log(`HTTP evidence: ${expected} accepted on first run, ${expected} duplicates on second run; final occupancy unchanged.`);
  } finally {server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
});
