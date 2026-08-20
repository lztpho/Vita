// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from 'vitest';
import { scaleRange, targetMatch } from './nutrition';

describe('deterministic nutrition helpers', () => {
  it('scores intake within the range as a full match', () => {
    expect(targetMatch({ min: 90, max: 110 }, { min: 80, max: 120 })).toBe(100);
  });

  it('penalizes values below and above a target symmetrically by ratio', () => {
    expect(targetMatch({ min: 40, max: 40 }, { min: 80, max: 120 })).toBe(50);
    expect(targetMatch({ min: 240, max: 240 }, { min: 80, max: 120 })).toBe(50);
  });

  it('scales both bounds without turning a range into one precise value', () => {
    expect(scaleRange({ min: 10, max: 14 }, 0.75)).toEqual({ min: 7.5, max: 10.5 });
  });
});
