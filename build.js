#!/usr/bin/env node
// Bundles src/<name>/index.js into providers/<name>.js.
// Usage: node build.js [name ...] [--minify]

const esbuild = require('esbuild');
const fs = require('fs');
const path = require('path');

const SRC_DIR = path.join(__dirname, 'src');
const OUT_DIR = path.join(__dirname, 'providers');

// Provided by the Nuvio runtime, never bundled.
const EXTERNAL = ['cheerio-without-node-native', 'react-native-cheerio', 'cheerio', 'crypto-js', 'axios'];

const args = process.argv.slice(2);
const minify = args.includes('--minify');
let names = args.filter((a) => !a.startsWith('-'));
if (names.length === 0) {
  names = fs.readdirSync(SRC_DIR, { withFileTypes: true })
    .filter((d) => d.isDirectory())
    .map((d) => d.name);
}

let failed = false;
for (const name of names) {
  const entry = path.join(SRC_DIR, name, 'index.js');
  if (!fs.existsSync(entry)) {
    console.error(`skip ${name}: missing ${entry}`);
    failed = true;
    continue;
  }
  const outfile = path.join(OUT_DIR, `${name}.js`);
  try {
    esbuild.buildSync({
      entryPoints: [entry],
      bundle: true,
      outfile,
      format: 'cjs',
      platform: 'neutral',
      // es2016 turns async/await into generators, which older Hermes builds need.
      target: 'es2016',
      minify,
      external: EXTERNAL,
      banner: { js: `/**\n * ${name} - built from src/${name}/\n */` },
      logLevel: 'warning',
    });
    console.log(`built providers/${name}.js (${(fs.statSync(outfile).size / 1024).toFixed(1)} KB)`);
  } catch (e) {
    console.error(`failed ${name}: ${e.message}`);
    failed = true;
  }
}
process.exit(failed ? 1 : 0);
