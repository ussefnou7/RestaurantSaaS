import { getDb } from './sqlite.mjs';
import { enqueueOrder, getAllOrders, countUnsyncedOrders, markSynced, purgeSyncedOrders } from './orderOutboxRepo.mjs';
const key = 'legacy-probe-paid-order';
const payload = { totalAmount: 100, label: 'طلب تجريبي — ليس طلب عميل' };
onmessage = async ({ data: phase }) => {
  try {
    const db = await getDb();
    let rows = getAllOrders(db);
    if (phase === 'write') {
      if (rows.length) throw new Error('Probe requires a fresh profile for write phase');
      enqueueOrder(db, payload, null, key);
      rows = getAllOrders(db);
    }
    if (rows.length !== 1 || rows[0].idempotencyKey !== key || JSON.stringify(rows[0].payload) !== JSON.stringify(payload) || countUnsyncedOrders(db) !== 1) {
      throw new Error('Persisted payload/key/pending count mismatch');
    }
    const userVersion = db.selectValue('PRAGMA user_version');
    if (phase === 'verify') {
      markSynced(db, rows[0].id, 123);
      purgeSyncedOrders(db);
      if (getAllOrders(db).length !== 0) throw new Error('Synced cleanup did not complete');
    }
    db.close();
    postMessage({ ok: true, phase, userVersion, secureContext: isSecureContext, checked: ['SQLite/WASM init','OPFS SAH pool','POS schema migrations','Arabic payload','stable idempotency key','pending count', ...(phase === 'verify' ? ['persistence across process restart','synced cleanup'] : [])] });
  } catch (error) { postMessage({ ok: false, error: String(error), stack: error.stack }); }
};
