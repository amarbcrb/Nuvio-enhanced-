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
  const expiry = today + 29;
  const key = await Core.createLicence(keys.privateKey, device, expiry);

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
    assert.strictEqual((await Core.readLicence(pub, await Core.createLicence(again.privateKey, device, expiry), device, today)).ok, true);
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
    const otherKey = await Core.createLicence(other.privateKey, device, expiry);
    assert.strictEqual((await Core.readLicence(keys.publicKey, otherKey, device, today)).reason, "signature");
  });
  await test("cle tronquee refusee", async () => assert.strictEqual((await Core.readLicence(keys.publicKey, key.slice(0, 100), device, today)).reason, "format"));
  await test("minuscules, espaces, tirets, O et I acceptes", async () => {
    const messy = " " + key.toLowerCase().replace(/(.{4})/g, "$1-").replace(/0/g, "o").replace(/1/g, "i") + "\n";
    assert.strictEqual((await Core.readLicence(keys.publicKey, messy, "k7f3 92qx", today)).ok, true);
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
      cases.push({ key: await Core.createLicence(keys.privateKey, device, expiry), device, today, expect: "valid" });
    }
    cases.push({ key, device, today: expiry + 1, expect: "expired" });
    cases.push({ key, device: "AAAA-BBBB", today, expect: "device" });
    cases.push({ key: key.slice(0, 5) + (key[5] === "Z" ? "Y" : "Z") + key.slice(6), device, today, expect: "signature" });
    fs.writeFileSync(out, JSON.stringify({ publicKey: keys.publicB64, cases }, null, 2));
  }
  console.log(passed + " tests ok");
})().catch((e) => { console.error(e); process.exit(1); });
