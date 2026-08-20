// SPDX-License-Identifier: Apache-2.0
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { describe, expect, it } from 'vitest';
import { ProgressBar } from './ProgressBar';

describe('ProgressBar', () => {
  it('separates intake and target ranges so narrow cards do not truncate them', async () => {
    Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { value: true, configurable: true });
    const host = document.createElement('div');
    document.body.append(host);
    const root = createRoot(host);

    await act(async () => { root.render(<ProgressBar compact metric={{
      key: 'caloriesKcal', label: '热量', unit: '千卡', intake: { min: 1008, max: 1452 },
      target: { min: 2105, max: 2215 }, mode: 'range', state: 'low', progress: 48,
    }} />); });

    expect(host.querySelector('.metric__header strong')?.textContent).toBe('热量');
    expect(host.querySelector('.metric__header span')?.textContent).toBe('1008–1452 千卡');
    expect(host.querySelector('.metric__target')?.textContent).toBe('目标 2105–2215');
    expect(host.querySelector('.metric__state')?.textContent).toBe('不足');

    await act(async () => { root.unmount(); });
    host.remove();
  });
});
