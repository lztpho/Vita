// SPDX-License-Identifier: Apache-2.0
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CalendarPage } from './CalendarPage';

const mocked = vi.hoisted(() => {
  const today = new Date();
  const localDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  const month = localDate.slice(0, 7);
  const nutrients = {
    caloriesKcal: { min: 396, max: 528 }, proteinG: { min: 5, max: 8 },
    carbohydrateG: { min: 65, max: 88 }, fatG: { min: 14, max: 20 },
    fiberG: { min: 5, max: 9 }, totalSugarG: { min: 35, max: 55 }, freeSugarG: { min: 30, max: 50 },
  };
  return {
    localDate,
    month,
    meal: {
      id: 'meal-1', revision: 1, status: 'confirmed', createdAtMs: today.getTime(), consumedAtMs: today.getTime(),
      mealType: 'late_night', mealTypeSource: 'automatic', recordingMethod: 'photo_analysis', overallMultiplier: 1,
      items: [{ id: 'item-1', name: '焦糖爆米花', amountLabel: '约 90–120 克', multiplier: 1, nutrients }],
      totals: nutrients, thumbnailCount: 1,
    },
    getMealThumbnail: vi.fn(async () => ({ dataUrl: 'data:image/webp;base64,AAAA' })),
    deleteMeal: vi.fn(async () => ({ deleted: true })),
  };
});

vi.mock('../native', () => ({
  VitaNative: {
    getNutritionMonth: vi.fn(async () => ({ month: mocked.month, days: [{ localDate: mocked.localDate, mealCount: 1, score: 82, complete: true }] })),
    getNutritionDay: vi.fn(async () => ({ summary: { localDate: mocked.localDate, metrics: [], meals: [mocked.meal], goalMatchScore: 82, complete: true } })),
    getMealThumbnail: mocked.getMealThumbnail,
    deleteMeal: mocked.deleteMeal,
  },
  errorText: (error: unknown) => String(error),
}));

describe('CalendarPage meal photos', () => {
  let host: HTMLDivElement;

  beforeEach(() => {
    Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { value: true, configurable: true });
    host = document.createElement('div');
    document.body.append(host);
    mocked.getMealThumbnail.mockClear();
    mocked.deleteMeal.mockClear();
  });

  afterEach(() => { host.remove(); });

  it('loads the saved thumbnail inline and opens it when tapped', async () => {
    const root = createRoot(host);
    await act(async () => {
      root.render(<CalendarPage refreshKey={0} />);
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 0)); });

    const image = host.querySelector('img[alt="夜宵照片 1"]') as HTMLImageElement;
    expect(mocked.getMealThumbnail).toHaveBeenCalledWith({ mealId: 'meal-1', index: 0, reference: false });
    expect(image?.src).toContain('data:image/webp;base64,AAAA');

    await act(async () => { image.closest('button')?.click(); });
    expect(host.querySelector('[role="dialog"] img')).not.toBeNull();
    await act(async () => { root.unmount(); });
  });

  it('requires confirmation before deleting a meal and refreshes the selected day', async () => {
    const root = createRoot(host);
    await act(async () => {
      root.render(<CalendarPage refreshKey={0} />);
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 0)); });

    const openDelete = [...host.querySelectorAll('button')].find((button) => button.textContent === '删除餐食');
    await act(async () => { openDelete?.click(); });
    expect(host.querySelector('[role="alertdialog"]')).not.toBeNull();
    expect(mocked.deleteMeal).not.toHaveBeenCalled();

    const confirmDelete = [...host.querySelectorAll('button')].find((button) => button.textContent === '确认删除');
    await act(async () => {
      confirmDelete?.click();
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(mocked.deleteMeal).toHaveBeenCalledWith({ mealId: 'meal-1' });
    expect(host.textContent).toContain('这餐已删除');
    await act(async () => { root.unmount(); });
  });
});
