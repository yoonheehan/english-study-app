// Finds word cards that are the same word twice and removes the extras. PC only: the app may add
// words but never delete them (see firestore.rules).
//
//   node tools/dedupe-words.mjs                 list the duplicates, change nothing
//   node tools/dedupe-words.mjs --apply         keep one card per group, mark the others deleted
//   node tools/dedupe-words.mjs --key C:\path\key.json
//
// Two cards are the same word when the spelling matches (ignoring case, extra spaces and a final
// period) and the part of speech matches; a blank part of speech matches any. "record (noun)" and
// "record (verb)" stay two cards. This is the rule in WordDuplicates.kt in the app.
//
// The card kept is the one with the most study progress (remembered, review stage, favorite), then
// the oldest. It takes the group's favorite and "study first" marks, and keeps its own progress.
// Extras are soft-deleted (deleted: true), like every delete, so devices remove them on next sync.
//
// The key is a Firebase service account key; default %USERPROFILE%\.firebase\englishstudy-admin.json.

import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const keyIndex = args.indexOf("--key");
const keyPath = keyIndex >= 0
  ? args[keyIndex + 1]
  : process.env.FIREBASE_KEY ?? path.join(os.homedir(), ".firebase", "englishstudy-admin.json");

const normalize = (word) => String(word ?? "").trim().replace(/\.+$/, "").replace(/\s+/g, " ").toLowerCase();
const pos = (value) => String(value ?? "").trim().replace(/\.+$/, "").toLowerCase();
const sameCard = (a, b) =>
  normalize(a.word) === normalize(b.word) && (!pos(a.partOfSpeech) || !pos(b.partOfSpeech) || pos(a.partOfSpeech) === pos(b.partOfSpeech));

/** Higher is more worth keeping. */
const score = (w) => [(w.mastery ?? 0) >= 100 ? 1 : 0, w.reviewStage ?? 0, w.favorite ? 1 : 0, -(w.createdAt ?? 0)];
const better = (a, b) => {
  const x = score(a), y = score(b);
  for (let i = 0; i < x.length; i++) if (x[i] !== y[i]) return x[i] > y[i] ? a : b;
  return a;
};

if (!fs.existsSync(keyPath)) throw new Error(`No service account key at ${keyPath}`);
const { initializeApp, cert } = await import("firebase-admin/app");
const { getFirestore, FieldValue } = await import("firebase-admin/firestore");
initializeApp({ credential: cert(JSON.parse(fs.readFileSync(keyPath, "utf8"))) });
const db = getFirestore();
const words = db.collection("vaults").doc("main").collection("words");

const snapshot = await words.get();
const cards = snapshot.docs.map((d) => ({ id: d.id, ...d.data() })).filter((c) => !c.deleted && c.word);

const bySpelling = new Map();
for (const card of cards) {
  const key = normalize(card.word);
  if (!bySpelling.has(key)) bySpelling.set(key, []);
  bySpelling.get(key).push(card);
}
const groups = [];
for (const same of bySpelling.values()) {
  const left = [...same];
  while (left.length > 0) {
    const first = left.shift();
    const group = [first, ...left.filter((c) => sameCard(first, c))];
    for (const c of group.slice(1)) left.splice(left.indexOf(c), 1);
    if (group.length > 1) groups.push(group);
  }
}

if (groups.length === 0) {
  console.log(`No duplicates among ${cards.length} words.`);
  process.exit(0);
}
let extras = 0;
for (const group of groups) {
  const keep = group.reduce(better);
  const drop = group.filter((c) => c !== keep);
  extras += drop.length;
  console.log(`${keep.word}${keep.partOfSpeech ? ` (${keep.partOfSpeech})` : ""}: keep "${keep.meaning}", remove ${drop.map((c) => `"${c.meaning}"`).join(", ")}`);
}
console.log(`\n${groups.length} words appear more than once; ${extras} extra cards.`);
if (!apply) {
  console.log("Nothing changed. Run again with --apply to remove the extra cards.");
  process.exit(0);
}

const now = Date.now();
let batch = db.batch();
let n = 0;
for (const group of groups) {
  const keep = group.reduce(better);
  const favorite = group.some((c) => c.favorite);
  const priority = group.some((c) => c.priority);
  if (favorite !== Boolean(keep.favorite) || priority !== Boolean(keep.priority)) {
    batch.update(words.doc(keep.id), { favorite, priority, updatedAt: now, syncedAt: FieldValue.serverTimestamp() });
    n++;
  }
  for (const c of group.filter((x) => x !== keep)) {
    batch.update(words.doc(c.id), { deleted: true, updatedAt: now, syncedAt: FieldValue.serverTimestamp() });
    n++;
    if (n >= 400) {
      await batch.commit();
      batch = db.batch();
      n = 0;
    }
  }
}
if (n > 0) await batch.commit();
console.log(`Removed ${extras} extra cards. Devices update on their next sync.`);
