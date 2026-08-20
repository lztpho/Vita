// SPDX-License-Identifier: Apache-2.0
import { readFile, mkdir, writeFile } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const build = new URL('../build/', import.meta.url);
const lock = JSON.parse(await readFile(new URL('package-lock.json', root), 'utf8'));
const npmRows = Object.entries(lock.packages ?? {})
  .filter(([path, value]) => path.startsWith('node_modules/') && value?.version && !value.dev)
  .map(([path, value]) => {
    const relative = path.slice('node_modules/'.length);
    const name = relative.split('/node_modules/').at(-1);
    return { name, version: value.version, license: value.license ?? 'UNKNOWN' };
  })
  .sort((a, b) => a.name.localeCompare(b.name) || a.version.localeCompare(b.version));

await mkdir(build, { recursive: true });
await writeFile(new URL('npm-licenses.json', build), `${JSON.stringify(npmRows, null, 2)}\n`);

const androidPath = new URL('../android/build/reports/cyclonedx/bom.json', import.meta.url);
let androidRows = [];
try {
  const bom = JSON.parse(await readFile(androidPath, 'utf8'));
  androidRows = (bom.components ?? []).filter((component) => !String(component.purl ?? '').includes('project_path=')).map((component) => ({
    name: [component.group, component.name].filter(Boolean).join(':'),
    version: component.version ?? 'UNKNOWN',
    license: component.group === 'net.zetetic' && component.name === 'sqlcipher-android'
      ? 'BSD-3-Clause'
      : (component.licenses ?? []).map((entry) => entry.license?.id ?? entry.license?.name).filter(Boolean).join(' OR ') || 'UNKNOWN',
    purl: component.purl ?? '',
  })).sort((a, b) => a.name.localeCompare(b.name) || a.version.localeCompare(b.version));
  await writeFile(new URL('android-licenses.json', build), `${JSON.stringify(androidRows, null, 2)}\n`);
  const unknown = androidRows.filter((row) => row.license === 'UNKNOWN');
  if (unknown.length) throw new Error(`Unknown Android runtime licenses: ${unknown.map((row) => `${row.name}@${row.version}`).join(', ')}`);
} catch (error) {
  if (process.argv.includes('--require-android')) throw new Error(`Android CycloneDX SBOM is required before generating complete notices: ${error.message}`);
}

const section = (title, rows) => [
  `## ${title}`,
  '',
  '| Component | Version | Declared license |',
  '| --- | --- | --- |',
  ...rows.map((row) => `| ${row.name.replaceAll('|', '\\|')} | ${row.version} | ${row.license.replaceAll('|', '\\|')} |`),
  '',
];
const notice = [
  '# Third-party notices',
  '',
  'Vita is licensed under Apache-2.0. The components below remain subject to their own licenses. This inventory is generated from the locked npm runtime tree and the resolved Android CycloneDX SBOM; an `UNKNOWN` license blocks release until reviewed.',
  '',
  'Complete machine-readable inventories are attached to each release. Source and license information for a component is available from its package registry and upstream project. License texts that require reproduction are stored in `THIRD_PARTY_LICENSES/`.',
  '',
  ...section('npm runtime components', npmRows),
  ...section('Android runtime components', androidRows),
].join('\n');
if (androidRows.length) await writeFile(new URL('THIRD_PARTY_NOTICES.md', root), `${notice.trimEnd()}\n`);
console.log(`Wrote ${npmRows.length} npm and ${androidRows.length} Android runtime license records.`);
