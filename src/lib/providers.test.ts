// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from 'vitest';
import { applyProviderPreset, detectProvider, normalizeProviderConfig, providerPreset } from './providers';

describe('provider presets', () => {
  it('detects providers while tolerating a trailing slash', () => {
    expect(detectProvider({ protocol: 'openai', baseUrl: 'https://api.minimaxi.com/v1/' })).toBe('minimax');
    expect(detectProvider({ protocol: 'openai', baseUrl: 'https://self-hosted.test/v1' })).toBe('custom');
  });

  it('starts an empty configuration with the OpenAI choice', () => {
    expect(detectProvider({ protocol: 'openai', baseUrl: '' })).toBe('openai');
  });

  it('fills the endpoint and clears model ids when the provider changes', () => {
    const current = { protocol: 'openai' as const, baseUrl: 'https://old.test/v1', visionModel: 'old-vision', textModel: 'old-text' };
    expect(applyProviderPreset(current, 'qwen')).toMatchObject({
      protocol: 'openai', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', visionModel: '', textModel: '', hasApiKey: false, configured: false,
    });
  });

  it('offers a MiniMax multimodal model hint', () => {
    expect(providerPreset('minimax').modelPlaceholder).toContain('MiniMax-M3');
  });

  it('fills missing native fields before rendering settings', () => {
    expect(normalizeProviderConfig({ configured: false, hasApiKey: false })).toEqual({
      protocol: 'openai', baseUrl: '', visionModel: '', textModel: '', configured: false, hasApiKey: false,
    });
  });
});
