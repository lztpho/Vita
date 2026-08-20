// SPDX-License-Identifier: Apache-2.0
import type { ProviderConfig, ProviderProtocol } from '../types';

export type ProviderPresetId =
  | 'openai'
  | 'anthropic'
  | 'gemini'
  | 'qwen'
  | 'hunyuan'
  | 'zhipu'
  | 'volcengine'
  | 'minimax'
  | 'siliconflow'
  | 'openrouter'
  | 'custom';

export interface ProviderPreset {
  id: ProviderPresetId;
  name: string;
  protocol: ProviderProtocol;
  baseUrl: string;
  modelPlaceholder: string;
  suggestedModels?: string[];
}

export const DEFAULT_PROVIDER_ID: ProviderPresetId = 'minimax';

export const PROVIDER_PRESETS: ProviderPreset[] = [
  {
    id: 'openai', name: 'OpenAI', protocol: 'openai', baseUrl: 'https://api.openai.com/v1',
    modelPlaceholder: '填写支持图片输入的模型 ID',
  },
  {
    id: 'anthropic', name: 'Anthropic Claude', protocol: 'anthropic', baseUrl: 'https://api.anthropic.com/v1',
    modelPlaceholder: '填写支持图片输入的 Claude 模型 ID',
  },
  {
    id: 'gemini', name: 'Google Gemini', protocol: 'openai', baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
    modelPlaceholder: '填写支持图片输入的 Gemini 模型 ID',
  },
  {
    id: 'qwen', name: '阿里云百炼 / 通义千问', protocol: 'openai', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    modelPlaceholder: '例如：qwen-vl-max', suggestedModels: ['qwen-vl-max'],
  },
  {
    id: 'hunyuan', name: '腾讯混元', protocol: 'openai', baseUrl: 'https://api.hunyuan.cloud.tencent.com/v1',
    modelPlaceholder: '例如：hunyuan-vision-1.5-instruct',
    suggestedModels: ['hunyuan-vision-1.5-instruct', 'hunyuan-t1-vision-20250916'],
  },
  {
    id: 'zhipu', name: '智谱 GLM', protocol: 'openai', baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    modelPlaceholder: '例如：glm-5v-turbo', suggestedModels: ['glm-5v-turbo', 'glm-4.5v'],
  },
  {
    id: 'volcengine', name: '火山方舟 / 豆包', protocol: 'openai', baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',
    modelPlaceholder: '例如：doubao-seed-2-0-lite-260215', suggestedModels: ['doubao-seed-2-0-lite-260215'],
  },
  {
    id: 'minimax', name: 'MiniMax', protocol: 'openai', baseUrl: 'https://api.minimaxi.com/v1',
    modelPlaceholder: '例如：MiniMax-M3',
  },
  {
    id: 'siliconflow', name: '硅基流动', protocol: 'openai', baseUrl: 'https://api.siliconflow.cn/v1',
    modelPlaceholder: '填写平台中支持图片输入的模型 ID',
  },
  {
    id: 'openrouter', name: 'OpenRouter', protocol: 'openai', baseUrl: 'https://openrouter.ai/api/v1',
    modelPlaceholder: '填写支持图片输入的 provider/model',
  },
  {
    id: 'custom', name: '自定义接口', protocol: 'openai', baseUrl: '',
    modelPlaceholder: 'model-id',
  },
];

export function providerPreset(id: ProviderPresetId): ProviderPreset {
  return PROVIDER_PRESETS.find((item) => item.id === id) || PROVIDER_PRESETS[PROVIDER_PRESETS.length - 1];
}

export function normalizeProviderConfig(config?: Partial<ProviderConfig>): ProviderConfig {
  return {
    protocol: config?.protocol === 'anthropic' ? 'anthropic' : 'openai',
    baseUrl: typeof config?.baseUrl === 'string' ? config.baseUrl : '',
    visionModel: typeof config?.visionModel === 'string' ? config.visionModel : '',
    textModel: typeof config?.textModel === 'string' ? config.textModel : '',
    hasApiKey: config?.hasApiKey === true,
    configured: config?.configured === true,
  };
}

function normalizedUrl(value: string): string {
  return value.trim().replace(/\/+$/, '').toLowerCase();
}

export function detectProvider(config?: Pick<ProviderConfig, 'baseUrl' | 'protocol'>): ProviderPresetId {
  if (!config?.baseUrl) return DEFAULT_PROVIDER_ID;
  const baseUrl = normalizedUrl(config.baseUrl);
  return PROVIDER_PRESETS.find((item) => item.id !== 'custom' && normalizedUrl(item.baseUrl) === baseUrl)?.id || 'custom';
}

export function applyProviderPreset(config: ProviderConfig, id: ProviderPresetId): ProviderConfig {
  const preset = providerPreset(id);
  if (id === 'custom') return { ...config, protocol: 'openai', baseUrl: '', visionModel: '', textModel: '', hasApiKey: false, configured: false };
  return { ...config, protocol: preset.protocol, baseUrl: preset.baseUrl, visionModel: '', textModel: '', hasApiKey: false, configured: false };
}
