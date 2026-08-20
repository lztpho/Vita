// SPDX-License-Identifier: Apache-2.0

export type ProviderProtocol = 'openai' | 'anthropic';

export interface ProviderConfig {
  protocol: ProviderProtocol;
  baseUrl: string;
  visionModel: string;
  textModel?: string;
  hasApiKey?: boolean;
  configured?: boolean;
}

export interface ProviderModel {
  id: string;
  name?: string;
  supportsImage?: boolean;
}

export interface Range {
  min: number;
  max: number;
}

export interface Nutrients {
  caloriesKcal: Range;
  proteinG: Range;
  carbohydrateG: Range;
  fatG: Range;
  fiberG: Range;
  totalSugarG: Range;
  freeSugarG: Range;
}

export type NutrientKey = keyof Nutrients;

export interface MealItem {
  id: string;
  name: string;
  amountLabel: string;
  multiplier: number;
  removed?: boolean;
  confidence?: 'high' | 'medium' | 'low';
  assumptions?: string[];
  nutrients: Nutrients;
}

export type MealType = 'breakfast' | 'lunch' | 'dinner' | 'snack' | 'late_night';

export interface MealDraft {
  id: string;
  consumedAtMs: number;
  mealType: MealType;
  mealTypeSource: 'automatic' | 'user_override';
  recordingMethod: 'photo_analysis' | 'historical_reuse';
  sourceMealId?: string;
  sourceRevision?: number;
  overallMultiplier: number;
  items: MealItem[];
  totals: Nutrients;
  thumbnailCount: number;
  referenceThumbnailCount?: number;
  notes?: string;
  confidence?: 'high' | 'medium' | 'low';
  nutritionSummary?: string;
  nutritionHighlights?: string[];
  nutritionAttention?: string[];
  assumptions?: string[];
  correctionConversation?: Array<{ role: 'user' | 'assistant'; content: string }>;
  status: 'draft' | 'confirmed';
}

export interface MealRecord extends Omit<MealDraft, 'status'> {
  revision: number;
  status: 'confirmed';
  createdAtMs: number;
}

export interface NutrientTarget {
  caloriesKcal: Range;
  proteinG: Range;
  carbohydrateG: Range;
  fatG: Range;
  fiberG: Range;
  freeSugarG: Range;
}

export interface NutritionGoalProposal {
  id: string;
  goalType: 'muscle_gain' | 'fat_loss' | 'recomposition' | 'maintenance';
  explanation: string;
  targets: NutrientTarget;
  warnings: string[];
  confirmed: false;
}

export interface NutritionGoal extends Omit<NutritionGoalProposal, 'confirmed'> {
  confirmed: true;
  effectiveFromMs: number;
}

export interface UserProfile {
  heightCm: number;
  birthDate: string;
  weightKg: number;
  equationSex: 'female' | 'male';
  activityLevel: 'sedentary' | 'light' | 'moderate' | 'high';
  goalType: NutritionGoalProposal['goalType'];
  generalHealthEligible: boolean;
}

export interface NutritionMetric {
  key: NutrientKey;
  label: string;
  unit: string;
  intake: Range;
  target?: Range;
  mode: 'range' | 'minimum' | 'maximum' | 'observe';
  state: 'low' | 'good' | 'high' | 'unknown';
  progress: number;
}

export interface NutritionDaySummary {
  localDate: string;
  metrics: NutritionMetric[];
  meals: MealRecord[];
  goal?: NutritionGoal;
  goalMatchScore?: number;
  complete: boolean;
}

export interface NutritionMonthDay {
  localDate: string;
  mealCount: number;
  score?: number;
  complete: boolean;
}

export interface NutritionMonth {
  month: string;
  days: NutritionMonthDay[];
}

export interface MealTemplate {
  mealId: string;
  revision: number;
  consumedAtMs: number;
  mealType: MealType;
  summary: string;
  thumbnailCount: number;
  caloriesKcal: Range;
  proteinG: Range;
  items: MealItem[];
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAtMs: number;
}

export interface ChatSession {
  id: string;
  title: string;
  messages: ChatMessage[];
  updatedAtMs: number;
}

export interface ModelTestResult {
  usable?: boolean;
  verified?: boolean;
  text: boolean;
  vision: boolean;
  structured: boolean;
  latencyMs: number;
  detail: string;
}

export interface AppState {
  provider: ProviderConfig;
  today: NutritionDaySummary;
  currentGoal?: NutritionGoal;
}

export interface SelectedImage {
  id: string;
  uri?: string;
  dataUrl?: string;
  previewUrl: string;
  name: string;
}
