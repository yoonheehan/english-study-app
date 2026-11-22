// Uploads the lesson and listening files in app/src/main/assets/content to Firestore,
// where the app reads them from (content/{level}/{lessons|listeningQr|listeningSets}/{id}).
//
//   node tools/upload-content.mjs --dry-run          check the files, upload nothing
//   node tools/upload-content.mjs                    upload every level
//   node tools/upload-content.mjs a1 b1              upload only these levels
//   node tools/upload-content.mjs --key C:\path\key.json
//
// The key is a Firebase service account key (admin access; it bypasses the security rules).
// Keep it outside the project and never commit or share it. Default location:
//   %USERPROFILE%\.firebase\englishstudy-admin.json   (or set FIREBASE_KEY)
//
// Each upload makes Firestore match the files exactly: items removed from a file are deleted
// from Firestore too. Devices pick the new content up the next time the app starts and show
// it from the start after that.

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const contentDir = path.join(root, "app", "src", "main", "assets", "content");

const args = process.argv.slice(2);
const dryRun = args.includes("--dry-run");
const keyIndex = args.indexOf("--key");
const keyPath = keyIndex >= 0
  ? args[keyIndex + 1]
  : process.env.FIREBASE_KEY ?? path.join(os.homedir(), ".firebase", "englishstudy-admin.json");
const wanted = args.filter((a, i) => !a.startsWith("--") && args[i - 1] !== "--key").map((a) => a.toLowerCase());

const COLLECTIONS = {
  lessons: (level) => readJson(level, "lessons.json"),
  listeningQr: (level) => readJson(level, "listening-tests.json").qr,
  listeningSets: (level) => readJson(level, "listening-tests.json").sets,
};

function readJson(level, file) {
  return JSON.parse(fs.readFileSync(path.join(contentDir, level, file), "utf8"));
}

/** Basic checks so a broken file never reaches the devices. The Gradle tests check much more. */
function check(level, collection, items) {
  if (!Array.isArray(items) || items.length === 0) throw new Error(`${level}/${collection}: no items`);
  const ids = new Set();
  for (const item of items) {
    if (typeof item.id !== "string" || !item.id.startsWith(`${level}-`) || item.id.includes("/")) {
      throw new Error(`${level}/${collection}: bad id ${JSON.stringify(item.id)}`);
    }
    if (ids.has(item.id)) throw new Error(`${level}/${collection}: duplicate id ${item.id}`);
    ids.add(item.id);
  }
}

const levels = fs.readdirSync(contentDir)
  .filter((name) => fs.statSync(path.join(contentDir, name)).isDirectory())
  .filter((name) => wanted.length === 0 || wanted.includes(name))
  .sort();
if (levels.length === 0) throw new Error(`No matching levels in ${contentDir}`);

const plan = levels.map((level) => {
  const collections = Object.entries(COLLECTIONS).map(([name, load]) => {
    const items = load(level);
    check(level, name, items);
    return { name, items };
  });
  return { level, collections };
});

for (const { level, collections } of plan) {
  console.log(`${level}: ` + collections.map((c) => `${c.name} ${c.items.length}`).join(", "));
}
if (dryRun) {
  console.log("Dry run: files look fine, nothing uploaded.");
  process.exit(0);
}

if (!fs.existsSync(keyPath)) {
  console.error(`Service account key not found: ${keyPath}`);
  console.error("Firebase console > Project settings > Service accounts > Generate new private key,");
  console.error("then save it there, or pass --key <path>.");
  process.exit(1);
}

const { initializeApp, cert } = await import("firebase-admin/app");
const { getFirestore, FieldValue } = await import("firebase-admin/firestore");
initializeApp({ credential: cert(JSON.parse(fs.readFileSync(keyPath, "utf8"))) });
const db = getFirestore();

let written = 0;
let deleted = 0;
for (const { level, collections } of plan) {
  const levelDoc = db.collection("content").doc(level);
  for (const { name, items } of collections) {
    const ref = levelDoc.collection(name);
    const keep = new Set(items.map((i) => i.id));
    const stale = (await ref.select().get()).docs.filter((d) => !keep.has(d.id));

    const writer = db.bulkWriter();
    items.forEach((item, order) => writer.set(ref.doc(item.id), { ...item, order }));
    stale.forEach((d) => writer.delete(d.ref));
    await writer.close();

    written += items.length;
    deleted += stale.length;
  }
  await levelDoc.set({ code: level, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  console.log(`${level}: uploaded`);
}
console.log(`Done: ${written} documents written, ${deleted} deleted.`);
