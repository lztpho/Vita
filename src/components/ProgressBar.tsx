// SPDX-License-Identifier: Apache-2.0
import type { NutritionMetric } from '../types';

function intakeText(metric: NutritionMetric) {
  const intake = metric.intake.min === metric.intake.max ? `${metric.intake.min}` : `${metric.intake.min}–${metric.intake.max}`;
  return `${intake} ${metric.unit}`;
}

function targetText(metric: NutritionMetric) {
  if (!metric.target) return '';
  return metric.mode === 'maximum' ? `目标 ≤ ${metric.target.max}` : `目标 ${metric.target.min}–${metric.target.max}`;
}

const stateText: Record<NutritionMetric['state'], string> = { low: '不足', good: '合适', high: '偏高', unknown: '待记录' };

export function ProgressBar({ metric, compact = false }: { metric: NutritionMetric; compact?: boolean }) {
  return (
    <div className={`metric ${compact ? 'metric--compact' : ''}`} data-state={metric.state}>
      <div className="metric__header">
        <strong>{metric.label}</strong>
        <span>{intakeText(metric)}</span>
      </div>
      {compact && metric.target && <small className="metric__target">{targetText(metric)}</small>}
      <div className="metric__track" role="progressbar" aria-label={`${metric.label}：${stateText[metric.state]}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(metric.progress)}>
        <span style={{ width: `${Math.max(0, Math.min(100, metric.progress))}%` }} />
      </div>
      <small className="metric__state">{stateText[metric.state]}</small>
    </div>
  );
}
