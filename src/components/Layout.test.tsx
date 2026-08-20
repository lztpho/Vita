// SPDX-License-Identifier: Apache-2.0
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { Layout } from './Layout';

describe('Layout parity', () => {
  it('keeps the intended navigation order and moves new conversation into the header', () => {
    const html = renderToStaticMarkup(<Layout view="chat" onView={vi.fn()} onNewConversation={vi.fn()}><div>messages</div></Layout>);
    expect(html).toContain('class="desktop-nav"');
    expect(html).toContain('class="app-header__actions"');
    expect(html).toContain('header-new-chat');
    const desktopNavigation = html.match(/<nav class="desktop-nav"[\s\S]*?<\/nav>/)?.[0] || '';
    expect(desktopNavigation).toMatch(/今日[\s\S]*咨询[\s\S]*拍餐[\s\S]*饮食记录/);
    expect(html).toContain('data-view="chat"');
  });
});
