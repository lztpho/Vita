// SPDX-License-Identifier: Apache-2.0
import { useState } from 'react';
import type { NutritionDaySummary } from '../types';
import { ProgressBar } from '../components/ProgressBar';
import { GoalDialog } from '../components/GoalDialog';
export function TodayPage({ summary, onRefresh }: { summary: NutritionDaySummary; onRefresh: () => void }) {
  const [goalOpen, setGoalOpen] = useState(false);
  return <section className="page page--today">
    <div className="today-layout"><div className="today-primary">
    <section className="nutrition-dashboard nutrition-dashboard--today">
      <div className="nutrition-dashboard__heading"><div><span className="eyebrow">{new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' })}</span><h1>今日摄入</h1></div>{summary.goalMatchScore != null && <div className="score"><strong>{summary.goalMatchScore}</strong><small>目标匹配</small></div>}</div>
      <p className="intake-status">{summary.meals.length ? `今天已确认 ${summary.meals.length} 餐` : '今天暂无已确认餐食'}</p>
      <div className="intake-today-summary"><article><span>今日热量</span><strong>{summary.metrics[0] ? `${summary.metrics[0].intake.min.toFixed(0)}–${summary.metrics[0].intake.max.toFixed(0)} 千卡` : '—'}</strong></article><article><span>已确认餐食</span><strong>{summary.meals.length} 餐</strong></article></div>
      {!summary.goal && <div className="inline-callout"><div><strong>还没有营养目标</strong><small>根据你的资料生成一份可修改的建议。</small></div><button className="button button--secondary" onClick={() => setGoalOpen(true)}>设置目标</button></div>}
      <div className="metrics-grid">{summary.metrics.map((metric) => <ProgressBar key={metric.key} metric={metric} compact />)}</div>
    </section>
    <section className="goal-plan-section"><div className="section-heading"><div><span className="eyebrow">目标计划</span><h2>阶段目标与评估</h2><p className="section-description">目标由你的资料与饮食记录生成，确认后才会生效。</p></div><button className="button button--secondary" onClick={() => setGoalOpen(true)}>{summary.goal ? '重新评估' : '开始设置'}</button></div>
    <div className="panel goal-summary">
      <div><span className="eyebrow">当前目标</span><h2>{summary.goal ? ({ muscle_gain: '增肌', fat_loss: '减脂', recomposition: '身体重组', maintenance: '维持' } as const)[summary.goal.goalType] : '尚未设置'}</h2>{summary.goal?.explanation && <p>{summary.goal.explanation}</p>}</div>
    </div></section>
    </div></div>
    {goalOpen && <GoalDialog onClose={() => setGoalOpen(false)} onConfirmed={onRefresh} />}
  </section>;
}
