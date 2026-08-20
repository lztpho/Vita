// SPDX-License-Identifier: Apache-2.0
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatPage } from './ChatPage';

const { streamChat } = vi.hoisted(() => ({ streamChat: vi.fn(async () => ({ runId: 'run-1' })) }));

vi.mock('../native', () => ({
  VitaNative: {
    getChatSession: vi.fn(async () => ({ session: { id: 'session-1', title: '新会话', messages: [], updatedAtMs: 1 } })),
    newChatSession: vi.fn(async () => ({ session: { id: 'session-2', title: '新会话', messages: [], updatedAtMs: 2 } })),
    streamChat,
    addListener: vi.fn(async () => ({ remove: async () => undefined })),
  },
  errorText: (error: unknown) => String(error),
}));

describe('ChatPage composer', () => {
  let host: HTMLDivElement;

  beforeEach(() => {
    Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { value: true, configurable: true });
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { value: vi.fn(), configurable: true });
    host = document.createElement('div');
    document.body.append(host);
    streamChat.mockClear();
  });

  afterEach(() => { host.remove(); });

  it('enables send immediately when a Chinese IME composition finishes', async () => {
    const root = createRoot(host);
    await act(async () => { root.render(<ChatPage />); });
    const textarea = host.querySelector('textarea') as HTMLTextAreaElement;
    const button = host.querySelector('button[aria-label="发送"]') as HTMLButtonElement;

    expect(textarea.checkValidity()).toBe(false);
    expect(button.disabled).toBe(false);
    textarea.value = '今天蛋白质还差多少';
    await act(async () => { textarea.dispatchEvent(new Event('compositionend', { bubbles: true })); });
    expect(textarea.checkValidity()).toBe(true);
    expect(button.disabled).toBe(false);

    await act(async () => { button.click(); });
    expect(streamChat).toHaveBeenCalledWith({ sessionId: 'session-1', message: '今天蛋白质还差多少' });
    expect(textarea.value).toBe('');
    await act(async () => { root.unmount(); });
  });

  it('keeps user message text readable on the dark bubble', () => {
    const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
    const styles = readFileSync(path.join(projectRoot, 'src/styles.css'), 'utf8');
    expect(styles).toContain('.message--user p, .message--user li, .message--user a { color: inherit; }');
  });
});
