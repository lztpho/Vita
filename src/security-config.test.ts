// SPDX-License-Identifier: Apache-2.0
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

describe('WebView security policy', () => {
  it('prevents web content from making direct network requests', () => {
    const html = readFileSync(path.join(process.cwd(), 'index.html'), 'utf8');
    expect(html).toContain("connect-src 'none'");
    expect(html).toContain("object-src 'none'");
    expect(html).toContain("frame-src 'none'");
    expect(html).toContain("base-uri 'none'");
  });
});
