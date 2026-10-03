// Sinh dữ liệu lịch sử và ngày demo từ các mẫu cùng thứ; không cần thư viện ngoài.
import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';

const dir = new URL('./data/', import.meta.url);
const target = '2026-10-03';
const demoAsOf = `${target} 07:30:00`;
const cutoff = '2026-09-22';
const seed = 'parkflow-exam-2026-10-03-v1';
const read = name => readFileSync(new URL(name, dir), 'utf8').replace(/^\uFEFF/, '');
const json = name => JSON.parse(read(name));
// Các CSV nguồn không có dấu phẩy hoặc xuống dòng trong trường dữ liệu.
function csv(name) {
  const lines = read(name).trimEnd().split(/\r?\n/);
  assert(!lines.some(line => line.includes('"')), `${name}: unexpected quoted field`);
  const header = lines.shift();
  const rows = lines.map(line => line.split(','));
  assert(rows.every(row => row.length === header.split(',').length), `${name}: column count`);
  return { header, rows };
}
const hash = value => createHash('sha256').update(value).digest('hex');
const random = value => parseInt(hash(`${seed}|${value}`).slice(0, 8), 16) / 2 ** 32;
const day = date => new Date(`${date}T00:00:00Z`).getUTCDay();
const next = date => new Date(Date.parse(`${date}T00:00:00Z`) + 86400000).toISOString().slice(0, 10);
const config = json('campus_config.json');
const scenarios = Object.fromEntries(Object.entries(json('scenarios.json')).filter(([date]) => date <= cutoff));
const historyCsv = csv('swipe_log.csv');
const scheduleCsv = csv('schedule.csv');
const original = historyCsv.rows.filter(row => row[0].slice(0, 10) <= cutoff);
const schedule = scheduleCsv.rows.filter(row => row[2] <= cutoff);
const groups = new Map();
for (const row of original) {
  const date = row[0].slice(0, 10);
  if (!groups.has(date)) groups.set(date, []);
  groups.get(date).push(row);
}
const dates = [...groups.keys()].sort();
const added = [];
const generation = [];
let demo;
function shiftTimestamp(timestamp, date, seconds) {
  if (!timestamp) return '';
  const time = timestamp.slice(11).split(':').map(Number);
  const shifted = time[0] * 3600 + time[1] * 60 + time[2] + seconds;
  return `${date} ${[Math.floor(shifted / 3600), Math.floor(shifted / 60) % 60, shifted % 60].map(n => String(n).padStart(2, '0')).join(':')}`;
}
function seconds(timestamp) {
  const [h, m, s] = timestamp.slice(11).split(':').map(Number);
  return h * 3600 + m * 60 + s;
}
for (let date = next(cutoff); date <= target; date = next(date)) {
  if (day(date) === 0) {
    scenarios[date] = { label: 'Chủ nhật nghỉ (mô phỏng)', kind: 'holiday' };
    continue;
  }
  const candidates = dates.filter(d => day(d) === day(date) && scenarios[d]?.kind === 'normal').slice(-3);
  assert(candidates.length, `Missing source weekday for ${date}`);
  const sourceDate = candidates[Math.floor(random(`${date}|source`) * candidates.length)];
  const factor = date === target ? 1.04 : 0.96 + random(`${date}|volume`) * 0.08;
  const rows = [];
  groups.get(sourceDate).forEach((row, index) => {
    const copies = factor < 1 ? (random(`${date}|${index}|keep`) < factor ? 1 : 0)
      : 1 + (random(`${date}|${index}|extra`) < factor - 1 ? 1 : 0);
    for (let copy = 0; copy < copies; copy++) {
      const jitter = Math.floor(random(`${date}|${index}|${copy}|time`) * 361) - 180;
      // Giữ thời lượng gửi xe và giới hạn 06:00–22:00 của bộ dữ liệu.
      const delta = Math.max(21600 - seconds(row[0]), Math.min(jitter, 79200 - seconds(row[1] || row[0])));
      rows.push([
        shiftTimestamp(row[0], date, delta), shiftTimestamp(row[1], date, delta), row[2],
        hash(`${seed}|${date}|${index}|${copy}|${row[3]}`), row[4], row[5],
      ]);
    }
  });
  rows.sort((a, b) => a[0].localeCompare(b[0]) || a[3].localeCompare(b[3]));
  if (date === target) demo = rows.filter(row => row[0] <= demoAsOf)
    .map(row => [row[0], row[1] && row[1] <= demoAsOf ? row[1] : '', ...row.slice(2)]);
  else added.push(...rows);
  const classes = scheduleCsv.rows.filter(row => row[2] === sourceDate);
  assert(classes.length, `Missing schedule for ${sourceDate}`);
  for (const row of classes) schedule.push([row[0], row[1], date, row[3], row[4], String(Math.max(1, Math.round(Number(row[5]) * factor)))]);
  scenarios[date] = { label: date === target ? 'Ngày thi 03/10 (mô phỏng thứ Bảy)' : 'Ngày thường (mô phỏng bổ sung)', kind: 'normal' };
  generation.push({ date, sourceDate, volumeFactor: Number(factor.toFixed(6)), sessions: date === target ? demo.length : rows.length, scheduleRows: classes.length });
}
const history = [...original, ...added];
const lots = new Set(config.lots.map(lot => lot.id));
const gates = new Set(config.gates.map(gate => gate.code));
const capacities = Object.fromEntries(config.lots.map(lot => [lot.id, lot.capacity]));
function validate(rows, isDemo) {
  const seen = new Set();
  const daily = new Map();
  let missingOut = 0;
  for (const row of rows) {
    const [tin, tout, gate, card, lot, vehicle] = row;
    assert(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(tin), `Invalid ts_in ${tin}`);
    assert(isDemo ? tin.slice(0, 10) === target : tin.slice(0, 10) < target, 'Demo/history date separation');
    if (isDemo) assert(tin <= demoAsOf && (!tout || tout <= demoAsOf), 'Event after demo cutoff');
    assert(!tout || (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(tout) && tout > tin && tout.slice(0, 10) === tin.slice(0, 10)), `Invalid ts_out ${tout}`);
    assert(lots.has(lot) && gates.has(gate), `Unknown lot/gate ${lot}/${gate}`);
    assert(/^[a-f0-9]{64}$/.test(card), 'Card must be synthetic SHA-256');
    assert(['Xe máy', 'Xe đạp', 'Ô tô'].includes(vehicle), `Unknown vehicle ${vehicle}`);
    const key = `${tin}|${card}|${lot}`;
    assert(!seen.has(key), `Duplicate session ${key}`);
    seen.add(key);
    if (!tout) missingOut++;
    const date = tin.slice(0, 10);
    if (!daily.has(date)) daily.set(date, { date, sessions: 0, missingOut: 0, events: [] });
    const entry = daily.get(date);
    entry.sessions++;
    if (!tout) entry.missingOut++;
    entry.events.push([tin, lot, 1]);
    if (tout) entry.events.push([tout, lot, -1]);
  }
  const summary = [...daily.values()].map(entry => {
    const current = Object.fromEntries([...lots].map(lot => [lot, 0]));
    const peak = { ...current };
    // Cùng giây: xử lý lượt ra trước lượt vào để không tạo đỉnh giả.
    entry.events.sort((a, b) => a[0].localeCompare(b[0]) || a[2] - b[2]);
    for (const [, lot, delta] of entry.events) {
      current[lot] += delta;
      assert(current[lot] >= 0, `Negative occupancy ${entry.date}/${lot}`);
      peak[lot] = Math.max(peak[lot], current[lot]);
    }
    return { date: entry.date, sessions: entry.sessions, missingOut: entry.missingOut, peak };
  });
  return { sessions: rows.length, events: rows.length * 2 - missingOut, missingOut, firstDate: summary[0].date, lastDate: summary.at(-1).date, daily: summary };
}
const historical = validate(history, false);
const demoReport = validate(demo, true);
const sameWeekdayDates = historical.daily.map(d => d.date).filter(d => day(d) === day(target)).slice(-4);
assert.deepEqual(sameWeekdayDates, ['2026-09-05', '2026-09-12', '2026-09-19', '2026-09-26']);
for (const date of sameWeekdayDates) {
  for (const lot of lots) assert(history.some(row => row[0].startsWith(date) && row[4] === lot), `Missing forecast baseline ${date}/${lot}`);
}
assert(schedule.some(row => row[2] === target), 'Missing demo schedule');
const scheduleKeys = new Set();
for (const row of schedule) {
  const key = `${row[0]}|${row[2]}`;
  assert(!scheduleKeys.has(key), `Duplicate schedule ${key}`);
  scheduleKeys.add(key);
}
const report = {
  source: 'synthetic', schemaVersion: 2, demoDate: target, demoAsOf, timezone: 'Asia/Ho_Chi_Minh', seed,
  demoOpenSessionNote: 'Blank ts_out means no observed exit by 07:30; it is not necessarily a data-quality error.',
  method: 'Resample original same-weekday normal sessions; volume 96–104%, demo 104%; shift both timestamps by at most 180 seconds within opening hours; preserve missing exits; regenerate synthetic card hashes.',
  capacities, originalHistoryPreservedThrough: cutoff, addedHistoricalSessions: added.length,
  historical, demo: demoReport, scheduleRows: schedule.length, sameWeekdayDates, generation,
  checks: { validSchema: true, validLotAndGate: true, noDuplicateSessions: true, nonnegativeDailyOccupancy: true, demoExcludedFromHistory: true, noDemoEventsAfterCutoff: true, fourSameWeekdaysForEveryLot: true },
};
const writeCsv = (name, header, rows) => writeFileSync(new URL(name, dir), `${header}\n${rows.map(row => row.join(',')).join('\n')}\n`, 'utf8');
// Tất cả kiểm tra phải qua trước khi ghi dữ liệu.
writeCsv('swipe_log.csv', historyCsv.header, history);
writeCsv('demo_log.csv', historyCsv.header, demo);
const vendorVehicleCodes = { 'Xe máy': 'XM', 'Xe đạp': 'XD', 'Ô tô': 'OTO' };
writeCsv('vendor_sample.csv', 'ThoiGianVao,ThoiGianRa,MaCong,MaThe,KhuVucGui,PhuongTien',
  demo.map(row => [...row.slice(0, 5), vendorVehicleCodes[row[5]]]));
writeCsv('schedule.csv', scheduleCsv.header, schedule);
writeFileSync(new URL('scenarios.json', dir), JSON.stringify(scenarios, null, 2) + '\n');
writeFileSync(new URL('validation_report.json', dir), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify({ historySessions: history.length, addedSessions: added.length, demoSessions: demo.length, scheduleRows: schedule.length, sameWeekdayDates, demoPeak: demoReport.daily[0].peak }, null, 2));
