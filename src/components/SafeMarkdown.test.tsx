// SPDX-License-Identifier: Apache-2.0
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { SafeMarkdown } from './SafeMarkdown';

describe('SafeMarkdown', () => {
  it('does not render raw HTML, scripts, event handlers, images, or javascript URLs', () => {
    const html = renderToStaticMarkup(<SafeMarkdown>{'<img src=x onerror=alert(1)><script>alert(2)</script> [bad](javascript:alert(3)) ![track](https://example.com/p.png)'}</SafeMarkdown>);
    expect(html).not.toContain('<img');
    expect(html).not.toContain('<script');
    expect(html).not.toContain('onerror');
    expect(html).not.toContain('javascript:');
    expect(html).not.toContain('href=');
  });

  it('allows only credential-free HTTPS links', () => {
    const html = renderToStaticMarkup(<SafeMarkdown>{'[safe](https://example.com/path) [http](http://example.com) [creds](https://user:pass@example.com)'}</SafeMarkdown>);
    expect(html).toContain('href="https://example.com/path"');
    expect(html.match(/href=/g)).toHaveLength(1);
    expect(html).toContain('rel="noopener noreferrer"');
  });
});
