// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from 'vitest';
import { applyProviderPreset, detectProvider, normalizeProviderConfig, providerPreset } from './providers';

describe('provider presets', () => {
  it('detects providers while tolerating a trailing slash', () => {
    expect(detectProvider({ protocol: 'openai', baseUrl: 'https://api.minimaxi.com/v1/' })).toBe('minimax');
    expect(detectProvider({ protocol: 'openai', baseUrl: 'https://api.hunyuan.cloud.tencent.com/v1/' })).toBe('hunyuan');
    expect(detectProvider({ protocol: 'openai', baseUrl: 'https://open.bigmodel.cn/api/paas/v4' })).toBe('zhipu');
    expect(detectProvider({ protocol: 'openai', baseUrl: 'https://ark.cn-beijing.volces.com/api/v3' })).toBe('volcengine');
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

  it('offers current domestic multimodal model hints', () => {
    expect(providerPreset('hunyuan').modelPlaceholder).toContain('hunyuan-vision');
    expect(providerPreset('hunyuan').suggestedModels).toContain('hunyuan-vision-1.5-instruct');
    expect(providerPreset('zhipu').modelPlaceholder).toContain('glm-5v');
    expect(providerPreset('zhipu').suggestedModels).toContain('glm-5v-turbo');
    expect(providerPreset('volcengine').modelPlaceholder).toContain('doubao-seed-2-0');
    expect(providerPreset('volcengine').suggestedModels).toContain('doubao-seed-2-0-lite-260215');
  });

  it('fills missing native fields before rendering settings', () => {
    expect(normalizeProviderConfig({ configured: false, hasApiKey: false })).toEqual({
      protocol: 'openai', baseUrl: '', visionModel: '', textModel: '', configured: false, hasApiKey: false,
    });
  });
});
