// SPDX-License-Identifier: Apache-2.0
import { Capacitor, registerPlugin, type PluginListenerHandle } from '@capacitor/core';
import type {
  AppState,
  ChatSession,
  MealDraft,
  MealRecord,
  MealTemplate,
  ModelTestResult,
  NutritionDaySummary,
  NutritionGoal,
  NutritionGoalProposal,
  NutritionMonth,
  ProviderConfig,
  ProviderModel,
  SelectedImage,
  UserProfile,
} from './types';

export interface MealAnalysisTask {
  taskId: string;
  draftId: string;
  status: 'none' | 'queued' | 'running' | 'succeeded' | 'failed';
  phase?: 'prepare' | 'upload' | 'model' | 'validate' | 'done' | 'failed';
  startedAt?: number;
  processedImages?: number;
  totalImages?: number;
  uploadPercent?: number;
  message?: string;
  draft?: MealDraft;
}

export interface VitaNativePlugin {
  getAppState(): Promise<AppState>;
  configureProvider(options: ProviderConfig): Promise<{ provider: ProviderConfig }>;
  promptApiKey(options?: { clear?: boolean; baseUrl?: string }): Promise<{ hasApiKey: boolean }>;
  listProviderModels(options: Pick<ProviderConfig, 'protocol' | 'baseUrl'>): Promise<{ models: ProviderModel[]; capabilityKnown: boolean }>;
  testProvider(): Promise<ModelTestResult>;
  analyzeMeal(options: {
    images: Array<Pick<SelectedImage, 'uri' | 'dataUrl' | 'name'>>;
    consumedAtMs: number;
    notes?: string;
  }): Promise<{ draft: MealDraft }>;
  startMealAnalysis(options: {
    images: Array<Pick<SelectedImage, 'uri' | 'dataUrl' | 'name'>>;
    consumedAtMs: number;
    notes?: string;
  }): Promise<{ taskId: string }>;
  getMealAnalysisTask(options: { taskId: string }): Promise<MealAnalysisTask>;
  getLatestMealAnalysisTask(): Promise<MealAnalysisTask>;
  forgetMealAnalysisTask(options: { taskId: string }): Promise<{ forgotten: boolean }>;
  listMealTemplates(options: { days: number; limit: number; query?: string }): Promise<{ meals: MealTemplate[] }>;
  createHistoricalReuseDraft(options: { mealId: string; revision: number; consumedAtMs: number }): Promise<{ draft: MealDraft }>;
  updateMealDraft(options: {
    draftId: string;
    consumedAtMs?: number;
    mealType?: string;
    overallMultiplier?: number;
    itemMultipliers?: Record<string, number>;
    removedItemIds?: string[];
  }): Promise<{ draft: MealDraft }>;
  refineMealDraft(options: { draftId: string; message: string }): Promise<{ draft: MealDraft }>;
  confirmMealDraft(options: { draftId: string }): Promise<{ meal: MealRecord }>;
  cancelMealDraft(options: { draftId: string }): Promise<void>;
  getTodayNutrition(): Promise<{ summary: NutritionDaySummary }>;
  getNutritionDay(options: { localDate: string }): Promise<{ summary: NutritionDaySummary }>;
  getNutritionMonth(options: { month: string }): Promise<NutritionMonth>;
  getMealThumbnail(options: { mealId: string; index: number; reference?: boolean }): Promise<{ dataUrl: string }>;
  deleteMeal(options: { mealId: string }): Promise<{ deleted: boolean }>;
  clearAllLocalData(options: { confirmed: boolean }): Promise<{ cleared: boolean }>;
  createGoalProposal(options: { profile: UserProfile }): Promise<{ proposal: NutritionGoalProposal }>;
  confirmGoal(options: { proposalId: string; targets: NutritionGoalProposal['targets'] }): Promise<{ goal: NutritionGoal }>;
  getChatSession(options?: { sessionId?: string }): Promise<{ session: ChatSession }>;
  newChatSession(): Promise<{ session: ChatSession }>;
  streamChat(options: { sessionId: string; message: string }): Promise<{ runId: string }>;
  addListener(eventName: 'chatDelta', listener: (event: { runId: string; delta: string }) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'chatDone', listener: (event: { runId: string; session: ChatSession }) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'chatError', listener: (event: { runId: string; message: string }) => void): Promise<PluginListenerHandle>;
}

const NativePlugin = registerPlugin<VitaNativePlugin>('Vita');

const zeroRange = () => ({ min: 0, max: 0 });
const emptyNutrients = () => ({
  caloriesKcal: zeroRange(), proteinG: zeroRange(), carbohydrateG: zeroRange(), fatG: zeroRange(),
  fiberG: zeroRange(), totalSugarG: zeroRange(), freeSugarG: zeroRange(),
});

function emptySummary(date = new Date().toISOString().slice(0, 10)): NutritionDaySummary {
  const labels = [
    ['caloriesKcal', '热量', '千卡', 'range'], ['proteinG', '蛋白质', '克', 'minimum'],
    ['carbohydrateG', '碳水', '克', 'range'], ['fatG', '脂肪', '克', 'range'],
    ['fiberG', '膳食纤维', '克', 'minimum'], ['totalSugarG', '总糖', '克', 'observe'],
    ['freeSugarG', '游离糖', '克', 'maximum'],
  ] as const;
  const nutrients = emptyNutrients();
  return {
    localDate: date,
    metrics: labels.map(([key, label, unit, mode]) => ({ key, label, unit, mode, intake: nutrients[key], state: 'unknown', progress: 0 })),
    meals: [], complete: true,
  };
}

class BrowserFallback implements VitaNativePlugin {
  private provider: ProviderConfig = { protocol: 'openai', baseUrl: '', visionModel: '', textModel: '', configured: false, hasApiKey: false };
  private goalProposal?: NutritionGoalProposal;
  private session: ChatSession = { id: crypto.randomUUID(), title: '新会话', messages: [], updatedAtMs: Date.now() };

  async getAppState(): Promise<AppState> { return { provider: this.provider, today: emptySummary() }; }
  async configureProvider(options: ProviderConfig) {
    this.provider = { ...options, hasApiKey: this.provider.hasApiKey, configured: Boolean(options.baseUrl && options.visionModel) };
    return { provider: this.provider };
  }
  async promptApiKey(options?: { clear?: boolean; baseUrl?: string }) {
    this.provider.hasApiKey = !options?.clear;
    return { hasApiKey: this.provider.hasApiKey };
  }
  async listProviderModels(): Promise<{ models: ProviderModel[]; capabilityKnown: boolean }> {
    return { models: [], capabilityKnown: false };
  }
  async testProvider(): Promise<ModelTestResult> {
    if (!this.provider.configured) throw new Error('请先保存服务地址和视觉模型。');
    return { usable: true, text: true, vision: true, structured: true, latencyMs: 42, detail: '预览模式' };
  }
  async analyzeMeal(): Promise<{ draft: MealDraft }> { throw new Error('浏览器预览不执行真实图片分析，请在 Android APK 中测试。'); }
  async startMealAnalysis(): Promise<{ taskId: string }> { throw new Error('浏览器预览不执行真实图片分析，请在 Android APK 中测试。'); }
  async getMealAnalysisTask(): Promise<MealAnalysisTask> { return { taskId: '', draftId: '', status: 'none' }; }
  async getLatestMealAnalysisTask(): Promise<MealAnalysisTask> { return { taskId: '', draftId: '', status: 'none' }; }
  async forgetMealAnalysisTask(): Promise<{ forgotten: boolean }> { return { forgotten: true }; }
  async listMealTemplates(): Promise<{ meals: MealTemplate[] }> { return { meals: [] }; }
  async createHistoricalReuseDraft(): Promise<{ draft: MealDraft }> { throw new Error('暂无可复用餐食'); }
  async updateMealDraft(): Promise<{ draft: MealDraft }> { throw new Error('暂无草稿'); }
  async refineMealDraft(): Promise<{ draft: MealDraft }> { throw new Error('浏览器预览不执行真实餐食修正，请在 Android APK 中测试。'); }
  async confirmMealDraft(): Promise<{ meal: MealRecord }> { throw new Error('暂无草稿'); }
  async cancelMealDraft(): Promise<void> {}
  async getTodayNutrition() { return { summary: emptySummary() }; }
  async getNutritionDay(options: { localDate: string }) { return { summary: emptySummary(options.localDate) }; }
  async getNutritionMonth(options: { month: string }) { return { month: options.month, days: [] }; }
  async getMealThumbnail(): Promise<{ dataUrl: string }> { return { dataUrl: '' }; }
  async deleteMeal(): Promise<{ deleted: boolean }> { return { deleted: true }; }
  async clearAllLocalData(): Promise<{ cleared: boolean }> {
    this.provider = { protocol: 'openai', baseUrl: '', visionModel: '', textModel: '', configured: false, hasApiKey: false };
    this.goalProposal = undefined;
    this.session = { id: crypto.randomUUID(), title: '新会话', messages: [], updatedAtMs: Date.now() };
    return { cleared: true };
  }
  async createGoalProposal(options: { profile: UserProfile }) {
    const weight = options.profile.weightKg;
    if (!options.profile.generalHealthEligible) throw new Error('此功能只面向一般健康成人。');
    const calories = options.profile.goalType === 'fat_loss' ? { min: 1800, max: 1900 } : { min: 2050, max: 2200 };
    this.goalProposal = {
      id: crypto.randomUUID(), goalType: options.profile.goalType,
      explanation: '请确认目标范围后使用。',
      targets: {
        caloriesKcal: calories, proteinG: { min: weight * 1.6, max: weight * 2 },
        carbohydrateG: { min: 180, max: 320 }, fatG: { min: 50, max: 75 },
        fiberG: { min: 25, max: 35 }, freeSugarG: { min: 0, max: 25 },
      }, warnings: [], confirmed: false,
    };
    return { proposal: this.goalProposal };
  }
  async confirmGoal(options: { proposalId: string; targets: NutritionGoalProposal['targets'] }): Promise<{ goal: NutritionGoal }> {
    if (!this.goalProposal || this.goalProposal.id !== options.proposalId) throw new Error('目标方案已失效');
    return { goal: { ...this.goalProposal, targets: options.targets, confirmed: true as const, effectiveFromMs: Date.now() } };
  }
  async getChatSession() { return { session: this.session }; }
  async newChatSession() {
    this.session = { id: crypto.randomUUID(), title: '新会话', messages: [], updatedAtMs: Date.now() };
    return { session: this.session };
  }
  async streamChat(options: { sessionId: string; message: string }): Promise<{ runId: string }> {
    this.session.messages.push({ id: crypto.randomUUID(), role: 'user', content: options.message, createdAtMs: Date.now() });
    throw new Error('浏览器预览不发送真实对话，请在 Android APK 中测试。');
  }
  async addListener(): Promise<PluginListenerHandle> { return { remove: async () => undefined }; }
}

export const VitaNative: VitaNativePlugin = Capacitor.isNativePlatform() ? NativePlugin : new BrowserFallback();

export function errorText(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === 'string') return error;
  return '操作没有完成，请稍后重试。';
}
