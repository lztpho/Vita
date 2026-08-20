// SPDX-License-Identifier: Apache-2.0
import type { Range } from '../types';

export function midpoint(value: Range): number {
  return (value.min + value.max) / 2;
}
export function targetMatch(intake: Range, target: Range): number {
  const value = midpoint(intake);
  if (value >= target.min && value <= target.max) return 100;
  if (value < target.min) return Math.max(0, Math.min(100, (value / Math.max(target.min, 1)) * 100));
  return Math.max(0, Math.min(100, (target.max / Math.max(value, 1)) * 100));
}

export function scaleRange(value: Range, factor: number): Range {
  if (!Number.isFinite(factor) || factor < 0) throw new Error('倍率无效');
  return { min: Math.round(value.min * factor * 10) / 10, max: Math.round(value.max * factor * 10) / 10 };
}
