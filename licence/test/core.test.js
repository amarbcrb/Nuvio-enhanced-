// Teste le format des licences de generateur.html avec le WebCrypto de Node (le meme que celui des navigateurs).
// Lancement : node licence/test/core.test.js [vecteurs.json]
"use strict";
const fs = require("fs");
const path = require("path");
const assert = require("assert");

const html = fs.readFileSync(path.join(__dirname, "..", "generateur.html"), "utf8");
const match = html.match(/<script id="licence-core">([\s\S]*?)<\/script>/);
assert(match, "bloc licence-core introuvable");
const Core = new Function(match[1] + "\nreturn LicenceCore;")();

let passed = 0;
async function test(name, fn) { await fn(); passed++; console.log("ok - " + name); }

(async () => {
  const keys = await Core.createKeys();
  const device = "K7F3-92QX";
  const today = 20397; // 5 novembre 2025
  const start = today;
  const expiry = today + 29;
  const key = await Core.createLicence(keys.privateKey, device, start, expiry);

  await test("base32 aller-retour", () => {
    for (let n = 0; n < 80; n++) {
      const bytes = Uint8Array.from({ length: n }, () => Math.floor(Math.random() * 256));
      assert.deepStrictEqual(Array.from(Core.decode(Core.encode(bytes))), Array.from(bytes));
    }
  });
  await test("longueur de la cle", () => assert.strictEqual(key.length, Core.KEY_CHARS));
  await test("cle valable", async () => {
    const r = await Core.readLicence(keys.publicKey, key, device, today);
    assert.strictEqual(r.ok, true);
    assert.strictEqual(r.expiryDay, expiry);
    assert.strictEqual(r.daysLeft, 29);
  });
  await test("sauvegarde rechargee : meme cle publique, cles toujours valables", async () => {
    const again = await Core.loadSecret(keys.secret);
    assert.strictEqual(again.publicB64, keys.publicB64);
    const pub = await Core.importPublic(keys.publicB64);
    assert.strictEqual((await Core.readLicence(pub, await Core.createLicence(again.privateKey, device, start, expiry), device, today)).ok, true);
  });
  await test("dernier jour encore valable", async () => assert.strictEqual((await Core.readLicence(keys.publicKey, key, device, expiry)).ok, true));
  await test("lendemain expire", async () => assert.strictEqual((await Core.readLicence(keys.publicKey, key, device, expiry + 1)).reason, "expired"));
  await test("autre telephone refuse", async () => assert.strictEqual((await Core.readLicence(keys.publicKey, key, "AAAA-BBBB", today)).reason, "device"));
  await test("cle modifiee refusee", async () => {
    const i = 3;
    const changed = key.slice(0, i) + (key[i] === "A" ? "B" : "A") + key.slice(i + 1);
    assert.strictEqual((await Core.readLicence(keys.publicKey, changed, device, today)).reason, "signature");
  });
  await test("autre cle secrete refusee", async () => {
    const other = await Core.createKeys();
    const otherKey = await Core.createLicence(other.privateKey, device, start, expiry);
    assert.strictEqual((await Core.readLicence(keys.publicKey, otherKey, device, today)).reason, "signature");
  });
  await test("cle tronquee refusee", async () => assert.strictEqual((await Core.readLicence(keys.publicKey, key.slice(0, 100), device, today)).reason, "format"));
  await test("minuscules, espaces, tirets, O et I acceptes", async () => {
    const messy = " " + key.toLowerCase().replace(/(.{4})/g, "$1-").replace(/0/g, "o").replace(/1/g, "i") + "\n";
    assert.strictEqual((await Core.readLicence(keys.publicKey, messy, "k7f3 92qx", today)).ok, true);
  });
  await test("pas encore valable avant le debut", async () => {
    const later = await Core.createLicence(keys.privateKey, device, today + 10, today + 40);
    const before = await Core.readLicence(keys.publicKey, later, device, today + 9);
    assert.strictEqual(before.reason, "notyet");
    assert.strictEqual(before.startDay, today + 10);
    assert.strictEqual((await Core.readLicence(keys.publicKey, later, device, today + 10)).ok, true);
  });
  await test("fin avant le debut refusee", async () => {
    await assert.rejects(Core.createLicence(keys.privateKey, device, today + 5, today + 4));
  });
  await test("dates du formulaire", () => {
    assert.strictEqual(Core.dayFromInput("2025-11-05"), 20397);
    assert.strictEqual(Core.dayToInput(20397), "2025-11-05");
    assert.strictEqual(Core.dayFromInput(""), null);
  });
  await test("cle avec nom : nom lu et signe", async () => {
    const named = await Core.createLicence(keys.privateKey, device, start, expiry, "Karim Benali");
    const r = await Core.readLicence(keys.publicKey, named, device, today);
    assert.strictEqual(r.ok, true);
    assert.strictEqual(r.name, "Karim Benali");
    assert(named.length > Core.KEY_CHARS);
  });
  await test("nom avec accents et arabe", async () => {
    for (const n of ["Amélie Dupré", "أمين بوشارب"]) {
      const k = await Core.createLicence(keys.privateKey, device, start, expiry, n);
      assert.strictEqual((await Core.readLicence(keys.publicKey, k, device, today)).name, n);
    }
  });
  await test("nom trop long coupe a 40 octets sans casser un caractere", async () => {
    const k = await Core.createLicence(keys.privateKey, device, start, expiry, "é".repeat(30));
    const r = await Core.readLicence(keys.publicKey, k, device, today);
    assert.strictEqual(r.name, "é".repeat(20));
  });
  await test("nom modifie refuse", async () => {
    const k = await Core.createLicence(keys.privateKey, device, start, expiry, "Karim");
    const bytes = Core.decode(Core.normalize(k)); bytes[12] ^= 1;
    assert.strictEqual((await Core.readLicence(keys.publicKey, Core.encode(bytes), device, today)).reason, "signature");
  });
  await test("cle sans nom : pas de nom", async () => {
    assert.strictEqual((await Core.readLicence(keys.publicKey, key, device, today)).name, null);
  });
  await test("caractere en trop a la fin refuse", async () => {
    assert.strictEqual((await Core.readLicence(keys.publicKey, key + "A", device, today)).reason, "format");
  });
  await test("format du code appareil", () => {
    assert.strictEqual(Core.formatDeviceCode("k7f392qx"), "K7F3-92QX");
    assert.strictEqual(Core.isDeviceCode("K7F3-92Q"), false);
  });

  const out = process.argv[2];
  if (out) {
    // Plusieurs cles valables : la conversion r||s vers DER doit marcher quel que soit le premier octet.
    const cases = [];
    for (let n = 0; n < 40; n++) {
      cases.push({ key: await Core.createLicence(keys.privateKey, device, start, expiry), device, today, expect: "valid" });
    }
    for (const n of ["Karim Benali", "Amélie Dupré", "أمين بوشارب"]) {
      cases.push({ key: await Core.createLicence(keys.privateKey, device, start, expiry, n), device, today, expect: "valid", name: n });
    }
    cases.push({ key, device, today: expiry + 1, expect: "expired" });
    cases.push({ key: await Core.createLicence(keys.privateKey, device, today + 10, today + 40), device, today, expect: "notyet" });
    cases.push({ key, device: "AAAA-BBBB", today, expect: "device" });
    cases.push({ key: key.slice(0, 5) + (key[5] === "Z" ? "Y" : "Z") + key.slice(6), device, today, expect: "signature" });
    fs.writeFileSync(out, JSON.stringify({ publicKey: keys.publicB64, cases }, null, 2));
  }
  console.log(passed + " tests ok");
})().catch((e) => { console.error(e); process.exit(1); });
