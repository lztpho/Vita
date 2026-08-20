// SPDX-License-Identifier: Apache-2.0
import type { NutritionMetric } from '../types';

function rangeText(metric: NutritionMetric) {
  const intake = metric.intake.min === metric.intake.max ? `${metric.intake.min}` : `${metric.intake.min}–${metric.intake.max}`;
  if (!metric.target) return `${intake} ${metric.unit}`;
  const target = metric.mode === 'maximum' ? `≤ ${metric.target.max}` : `${metric.target.min}–${metric.target.max}`;
  return `${intake} / ${target} ${metric.unit}`;
}

const stateText: Record<NutritionMetric['state'], string> = { low: '不足', good: '合适', high: '偏高', unknown: '待记录' };

export function ProgressBar({ metric, compact = false }: { metric: NutritionMetric; compact?: boolean }) {
  return (
    <div className={`metric ${compact ? 'metric--compact' : ''}`} data-state={metric.state}>
      <div className="metric__header">
        <strong>{metric.label}</strong>
        <span>{rangeText(metric)}</span>
      </div>
      <div className="metric__track" role="progressbar" aria-label={`${metric.label}：${stateText[metric.state]}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(metric.progress)}>
        <span style={{ width: `${Math.max(0, Math.min(100, metric.progress))}%` }} />
      </div>
      <small>{stateText[metric.state]}</small>
    </div>
  );
}
