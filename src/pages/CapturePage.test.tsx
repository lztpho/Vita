// SPDX-License-Identifier: Apache-2.0
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MealCorrection, RecognitionDetails } from './CapturePage';

describe('MealCorrection', () => {
  let host: HTMLDivElement;

  beforeEach(() => {
    Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { value: true, configurable: true });
    host = document.createElement('div');
    document.body.append(host);
  });

  afterEach(() => { host.remove(); });

  it('reads the current Android IME value directly when sending a correction', async () => {
    const onRefine = vi.fn(async () => true);
    const root = createRoot(host);
    await act(async () => { root.render(<MealCorrection busy={false} onRefine={onRefine} />); });
    const textarea = host.querySelector('textarea') as HTMLTextAreaElement;
    const button = host.querySelector('button') as HTMLButtonElement;

    expect(button.disabled).toBe(false);
    textarea.value = '只吃了一份双蛋的肉肠粉';
    await act(async () => { button.click(); });

    expect(onRefine).toHaveBeenCalledWith('只吃了一份双蛋的肉肠粉');
    expect(textarea.value).toBe('');
    await act(async () => { root.unmount(); });
  });
});

describe('meal image pipeline', () => {
  it('uploads only sanitized JPEG bytes and keeps an encrypted reduced thumbnail', () => {
    const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
    const vault = readFileSync(path.join(projectRoot, 'android/app/src/main/java/io/github/lztpho/vita/nativebridge/ImageVault.kt'), 'utf8');
    const capture = readFileSync(path.join(projectRoot, 'src/pages/CapturePage.tsx'), 'utf8');

    expect(vault).toContain('val encoded = compressImage(flattened, Bitmap.CompressFormat.JPEG, 90)');
    expect(vault).toContain('stripJpegMetadata(encoded)');
    expect(vault).toContain('Base64.encodeToString(sanitized.modelBytes, Base64.NO_WRAP)');
    expect(vault).not.toContain('Base64.encodeToString(bytes, Base64.NO_WRAP)');
    expect(vault).toContain('val thumbnail = resizeImage(flattened, 768)');
    expect(vault).toContain('modelBytes.size <= ImageVault.MAX_UPLOAD_BYTES');
    expect(vault).toContain('bytes.fill(0)');
    expect(capture).toContain('quality: 100');
    expect(capture).toContain('correctOrientation: false');
  });
});

describe('RecognitionDetails', () => {
  it('uses the concise private-edition information hierarchy', async () => {
    const host = document.createElement('div');
    document.body.append(host);
    const root = createRoot(host);
    const range = (min: number, max: number) => ({ min, max });
    await act(async () => { root.render(<RecognitionDetails items={[{
      id: 'food-1', name: '烤鸡腿', amountLabel: '420–480 克', multiplier: 1,
      nutrients: {
        caloriesKcal: range(840, 1050), proteinG: range(60, 70), carbohydrateG: range(0, 4),
        fatG: range(62, 82), fiberG: range(0, 0.7), totalSugarG: range(0, 1.5), freeSugarG: range(0, 0.8),
      },
    }]} assumptions={['按可见份量估算']} />); });

    expect(host.querySelector('.meal-food-list')?.textContent).toBe('烤鸡腿420–480 克 · 840–1050 千卡');
    expect(host.querySelector('.meal-food-list')?.textContent).not.toContain('蛋白质');
    expect(host.querySelector('.analysis-assumptions')?.textContent).toContain('估算依据按可见份量估算');
    await act(async () => { root.unmount(); });
    host.remove();
  });
});
