// SPDX-License-Identifier: Apache-2.0
import { readFile } from 'node:fs/promises';

const npm = JSON.parse(await readFile(new URL('../build/npm-licenses.json', import.meta.url), 'utf8'));
const android = JSON.parse(await readFile(new URL('../build/android-licenses.json', import.meta.url), 'utf8'));
const packages = [
  ...npm.map((entry) => ({ ecosystem: 'npm', name: entry.name, version: entry.version })),
  ...android.map((entry) => ({ ecosystem: 'Maven', name: entry.name, version: entry.version })),
];
const response = await fetch('https://api.osv.dev/v1/querybatch', {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ queries: packages.map(({ ecosystem, name, version }) => ({ package: { ecosystem, name }, version })) }),
  signal: AbortSignal.timeout(60_000),
});
if (!response.ok) throw new Error(`OSV query failed with HTTP ${response.status}`);
const result = await response.json();
const findings = result.results.flatMap((entry, index) => (entry.vulns ?? []).map((vulnerability) => ({
  package: `${packages[index].ecosystem}:${packages[index].name}@${packages[index].version}`,
  id: vulnerability.id,
})));
if (findings.length) {
  console.error(JSON.stringify(findings, null, 2));
  throw new Error(`OSV found ${findings.length} known vulnerabilities`);
}
console.log(`OSV queried ${packages.length} locked runtime packages; no known vulnerabilities found.`);
