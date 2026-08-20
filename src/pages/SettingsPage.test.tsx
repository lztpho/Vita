// SPDX-License-Identifier: Apache-2.0
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SettingsPage } from './SettingsPage';

const mocked = vi.hoisted(() => ({
  promptApiKey: vi.fn(async () => ({ hasApiKey: true, baseUrl: 'https://models.example.test/v1' })),
  exportDiagnosticLogs: vi.fn(async () => ({ exported: true })),
  listProviderModels: vi.fn(async () => ({ capabilityKnown: true, models: [
    { id: 'qwen-vl-max', name: 'Qwen VL Max' },
    { id: 'qwen3-omni-flash-2025-12-01' },
  ] })),
}));

vi.mock('../native', () => ({
  VitaNative: {
    promptApiKey: mocked.promptApiKey,
    configureProvider: vi.fn(),
    listProviderModels: mocked.listProviderModels,
    testProvider: vi.fn(),
    exportDiagnosticLogs: mocked.exportDiagnosticLogs,
    clearAllLocalData: vi.fn(),
  },
  errorText: (error: unknown) => String(error),
}));

describe('SettingsPage API Key setup', () => {
  let host: HTMLDivElement;

  beforeEach(() => {
    Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { value: true, configurable: true });
    mocked.promptApiKey.mockClear();
    mocked.exportDiagnosticLogs.mockClear();
    mocked.listProviderModels.mockClear();
    host = document.createElement('div');
    document.body.append(host);
  });

  afterEach(() => { host.remove(); });

  it('shows MiniMax for a new unconfigured installation', async () => {
    const root = createRoot(host);
    await act(async () => {
      root.render(<SettingsPage provider={{ protocol: 'openai', baseUrl: '', visionModel: '', textModel: '', hasApiKey: false, configured: false }} onSaved={vi.fn()} />);
    });

    expect(host.querySelector('.provider-select-button')?.textContent).toContain('MiniMax');
    expect((host.querySelector('input[type="url"]') as HTMLInputElement).value).toBe('https://api.minimaxi.com/v1');
    await act(async () => { root.unmount(); });
  });

  it('opens secure key entry before a custom endpoint has been configured', async () => {
    const root = createRoot(host);
    await act(async () => {
      root.render(<SettingsPage provider={{ protocol: 'openai', baseUrl: '', visionModel: '', textModel: '', hasApiKey: false, configured: false }} onSaved={vi.fn()} />);
    });

    const provider = host.querySelector('.provider-select-button') as HTMLButtonElement;
    await act(async () => { provider.click(); });
    expect(host.querySelector('.provider-picker-dialog')).not.toBeNull();
    const customProvider = [...host.querySelectorAll('.provider-option')].find((button) => button.textContent === '自定义接口') as HTMLButtonElement;
    await act(async () => { customProvider.click(); });
    expect(host.querySelector('.provider-picker-dialog')).toBeNull();
    expect(host.querySelector('.provider-select-button')?.textContent).toContain('自定义接口');
    const keyButton = [...host.querySelectorAll('button')].find((button) => button.textContent === '填写 Key') as HTMLButtonElement;
    expect(keyButton.disabled).toBe(false);

    await act(async () => { keyButton.click(); });

    expect(mocked.promptApiKey).toHaveBeenCalledWith({ clear: false, baseUrl: '', protocol: 'openai' });
    const endpoint = host.querySelector('input[type="url"]') as HTMLInputElement;
    expect(endpoint.value).toBe('https://models.example.test/v1');
    expect(host.querySelector('.settings-notice')?.textContent).toBe('API Key 已保存');
    await act(async () => { root.unmount(); });
  });

  it('shows a concise sponsor link below model settings', async () => {
    const root = createRoot(host);
    await act(async () => {
      root.render(<SettingsPage provider={{ protocol: 'openai', baseUrl: '', visionModel: '', textModel: '', hasApiKey: false, configured: false }} onSaved={vi.fn()} />);
    });

    const sponsor = host.querySelector('.sponsor-card');
    const links = [...(sponsor?.querySelectorAll('a') || [])] as HTMLAnchorElement[];
    expect(sponsor?.textContent).toContain('如果觉得有用的话，可以请作者吃一斤无籽西瓜 🍉');
    expect(sponsor?.textContent).toContain('点击请作者吃瓜');
    expect(sponsor?.textContent).toContain('🍉');
    expect(sponsor?.textContent).not.toContain('支持 Vita');
    expect(sponsor?.textContent).not.toContain('赞助完全自愿');
    expect(sponsor?.querySelector('img')).toBeNull();
    expect(links).toHaveLength(1);
    expect(links.every((link) => link.href === 'https://afdian.com/a/lztpho')).toBe(true);
    expect(links.every((link) => link.target === '_blank' && link.rel === 'noopener noreferrer')).toBe(true);

    await act(async () => { root.unmount(); });
  });

  it('exports a redacted diagnostic log from the data section', async () => {
    const root = createRoot(host);
    await act(async () => {
      root.render(<SettingsPage provider={{ protocol: 'openai', baseUrl: '', visionModel: '', textModel: '', hasApiKey: false, configured: false }} onSaved={vi.fn()} />);
    });

    const exportButton = [...host.querySelectorAll('button')].find((button) => button.textContent === '导出诊断日志') as HTMLButtonElement;
    await act(async () => { exportButton.click(); });

    expect(mocked.exportDiagnosticLogs).toHaveBeenCalledOnce();
    expect(host.querySelector('.settings-information [role="status"]')?.textContent).toBe('诊断日志已导出');
    const information = host.querySelector('.settings-information') as HTMLElement;
    const sponsor = host.querySelector('.sponsor-card') as HTMLElement;
    expect(information.compareDocumentPosition(sponsor) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(information?.textContent).not.toContain('餐食图片');
    expect(information?.textContent).not.toContain('遥测');
    const sourceLink = host.querySelector('.settings-source-link') as HTMLAnchorElement;
    expect(sourceLink.textContent).toBe('GitHub 开源地址');
    expect(sourceLink.href).toBe('https://github.com/lztpho/Vita');
    await act(async () => { root.unmount(); });
  });

  it('uses a searchable in-app model picker instead of a native select', async () => {
    const root = createRoot(host);
    await act(async () => {
      root.render(<SettingsPage provider={{ protocol: 'openai', baseUrl: 'https://api.minimaxi.com/v1', visionModel: '', textModel: '', hasApiKey: true, configured: false }} onSaved={vi.fn()} />);
    });

    const loadButton = [...host.querySelectorAll('button')].find((button) => button.textContent === '获取模型') as HTMLButtonElement;
    await act(async () => { loadButton.click(); });

    expect(host.querySelector('.model-picker-dialog')).not.toBeNull();
    expect(host.querySelector('.model-picker select')).toBeNull();
    const search = host.querySelector('input[type="search"]') as HTMLInputElement;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(search, 'flash');
      search.dispatchEvent(new Event('input', { bubbles: true }));
    });
    expect(host.querySelector('.model-option-list')?.textContent).toContain('qwen3-omni-flash-2025-12-01');
    expect(host.querySelector('.model-option-list')?.textContent).not.toContain('qwen-vl-max');

    const option = host.querySelector('.model-option') as HTMLButtonElement;
    await act(async () => { option.click(); });
    expect(host.querySelector('.model-picker-dialog')).toBeNull();
    expect(host.querySelector('.model-picker .model-select-button')?.textContent).toContain('qwen3-omni-flash-2025-12-01');
    await act(async () => { root.unmount(); });
  });
});
