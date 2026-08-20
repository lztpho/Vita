// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useState } from 'react';
import type { MealRecord, NutritionDaySummary, NutritionMonth } from '../types';
import { VitaNative, errorText } from '../native';
import { ProgressBar } from '../components/ProgressBar';
import { Icon } from '../components/Icons';

function monthValue(date = new Date()) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`; }
function localDateValue(date = new Date()) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
function mealLabel(meal: MealRecord) { return ({ breakfast: '早餐', lunch: '午餐', dinner: '晚餐', snack: '加餐', late_night: '夜宵' } as const)[meal.mealType]; }

function MealPhoto({ meal, index, onOpen, onError }: {
  meal: MealRecord;
  index: number;
  onOpen: (dataUrl: string) => void;
  onError: (message: string) => void;
}) {
  const [dataUrl, setDataUrl] = useState('');
  const [failed, setFailed] = useState(false);
  const reference = meal.recordingMethod === 'historical_reuse';

  useEffect(() => {
    let alive = true;
    setDataUrl('');
    setFailed(false);
    void VitaNative.getMealThumbnail({ mealId: meal.id, index, reference })
      .then((result) => { if (alive) setDataUrl(result.dataUrl); })
      .catch((error) => {
        if (!alive) return;
        setFailed(true);
        onError(errorText(error));
      });
    return () => { alive = false; };
  }, [index, meal.id, reference, onError]);

  if (failed) return <div className="meal-photo-placeholder"><Icon name="image" /><span>照片暂时无法读取</span></div>;
  return <button className="meal-photo-thumb" disabled={!dataUrl} onClick={() => onOpen(dataUrl)} aria-label={`放大查看${reference ? '历史参考' : mealLabel(meal)}照片 ${index + 1}`}>
    {dataUrl ? <img src={dataUrl} alt={`${reference ? '历史参考' : mealLabel(meal)}照片 ${index + 1}`} /> : <span className="meal-photo-loading">正在加载照片…</span>}
  </button>;
}

export function CalendarPage({ refreshKey }: { refreshKey: number }) {
  const [month, setMonth] = useState(monthValue());
  const [calendar, setCalendar] = useState<NutritionMonth>({ month, days: [] });
  const [selected, setSelected] = useState(localDateValue());
  const [detail, setDetail] = useState<NutritionDaySummary>();
  const [notice, setNotice] = useState('');
  const [photo, setPhoto] = useState('');
  const [deleteMeal, setDeleteMeal] = useState<MealRecord>();
  const [deleteVersion, setDeleteVersion] = useState(0);
  const [deleting, setDeleting] = useState(false);
  const showPhotoError = useMemo(() => (message: string) => setNotice(message), []);

  const byDate = useMemo(() => new Map(calendar.days.map((day) => [day.localDate, day])), [calendar]);
  const first = new Date(`${month}-01T12:00:00`);
  const cells = Array.from({ length: new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate() }, (_, index) => `${month}-${String(index + 1).padStart(2, '0')}`);
  const padding = Array.from({ length: (first.getDay() + 6) % 7 });

  useEffect(() => { void (async () => { try { setCalendar(await VitaNative.getNutritionMonth({ month })); } catch (error) { setNotice(errorText(error)); } })(); }, [month, refreshKey, deleteVersion]);
  useEffect(() => {
    if (calendar.month !== month || !calendar.days.length || calendar.days.some((day) => day.localDate === selected)) return;
    const today = localDateValue();
    const next = calendar.days.find((day) => day.localDate === today)?.localDate
      || [...calendar.days].reverse().find((day) => day.mealCount > 0)?.localDate
      || calendar.days[0].localDate;
    setSelected(next);
  }, [calendar, selected]);
  useEffect(() => { if (!selected) return; void (async () => { try { setDetail((await VitaNative.getNutritionDay({ localDate: selected })).summary); } catch (error) { setNotice(errorText(error)); } })(); }, [selected, refreshKey, deleteVersion]);

  function shift(delta: number) { const next = new Date(`${month}-01T12:00:00`); next.setMonth(next.getMonth() + delta); setMonth(monthValue(next)); setSelected(''); setDetail(undefined); }

  async function confirmDeleteMeal() {
    if (!deleteMeal || deleting) return;
    setDeleting(true);
    try {
      await VitaNative.deleteMeal({ mealId: deleteMeal.id });
      setDeleteMeal(undefined);
      setNotice('这餐已删除，营养统计已经更新。');
      setDeleteVersion((value) => value + 1);
    } catch (error) {
      setNotice(errorText(error));
    } finally {
      setDeleting(false);
    }
  }

  return <section className="page page--calendar">
    <section className="panel nutrition-calendar-panel">
      <div className="calendar-toolbar"><p>点开某一天，查看营养进度和每餐明细。</p><div className="calendar-navigation"><button onClick={() => shift(-1)} aria-label="上一月">‹</button><strong>{first.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long' })}</strong><button onClick={() => shift(1)} aria-label="下一月">›</button><button onClick={() => { setMonth(monthValue()); setSelected(localDateValue()); }}>本月</button></div></div>
      <div className="weekdays">{'一二三四五六日'.split('').map((day) => <span key={day}>{day}</span>)}</div>
      <div className="calendar-grid" role="grid">{padding.map((_, index) => <span key={`pad-${index}`} />)}{cells.map((date) => { const day = byDate.get(date); const today = date === localDateValue(); const states = [today ? 'today' : '', date === selected ? 'selected' : '', !day?.mealCount ? 'empty' : '', day?.mealCount && !day.complete ? 'incomplete' : ''].filter(Boolean).join(' '); const band = day?.score == null ? 'unknown' : day.score >= 90 ? 'high' : day.score >= 70 ? 'medium' : 'low'; return <button key={date} data-states={states} data-band={band} aria-selected={date === selected} onClick={() => setSelected(date)}><strong>{Number(date.slice(-2))}</strong><span>{day?.score ?? '—'}</span><small>{today && !day?.mealCount ? '进行中' : day?.mealCount ? (!day.complete ? '不完整' : `${day.mealCount}餐`) : '无记录'}</small></button>; })}</div>
      <div className="calendar-legend"><span><i data-kind="today" />今天</span><span><i data-kind="selected" />已选择</span><span><i data-kind="empty" />无记录</span><span><i data-kind="incomplete" />数据不完整</span></div>
    </section>

    {detail && <section className="selected-nutrition-day">
      <div className="section-line"><div><span className="eyebrow">{new Date(`${detail.localDate}T12:00:00`).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' })}</span><h2>{detail.meals.length ? `${detail.meals.length} 餐已确认` : '当天没有记录'}</h2></div>{detail.goalMatchScore != null && <div className="score"><strong>{detail.goalMatchScore}</strong><small>目标匹配</small></div>}</div>
      {detail.meals.length > 0 && <div className="detail-metrics">{detail.metrics.map((metric) => <ProgressBar key={metric.key} metric={metric} compact />)}</div>}
      {detail.meals.map((meal) => <article className="meal-detail panel" key={meal.id}>
        <header><div><span className="meal-badge">{mealLabel(meal)}</span><h3>{new Date(meal.consumedAtMs).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}</h3></div><small>{meal.recordingMethod === 'historical_reuse' ? '历史复用' : `${meal.thumbnailCount} 张照片`}</small></header>
        {(meal.thumbnailCount > 0 || meal.referenceThumbnailCount) && <div className="meal-photos">{Array.from({ length: meal.thumbnailCount || meal.referenceThumbnailCount || 0 }, (_, index) => <MealPhoto key={index} meal={meal} index={index} onOpen={setPhoto} onError={showPhotoError} />)}</div>}
        <div className="food-table">{meal.items.filter((item) => !item.removed).map((item) => <div key={item.id}><strong>{item.name}</strong><span>{item.amountLabel}</span></div>)}</div>
        <div className="meal-nutrients"><span>热量 <b>{meal.totals.caloriesKcal.min.toFixed(0)}–{meal.totals.caloriesKcal.max.toFixed(0)} 千卡</b></span><span>蛋白质 <b>{meal.totals.proteinG.min.toFixed(1)}–{meal.totals.proteinG.max.toFixed(1)} 克</b></span><span>总糖 <b>{meal.totals.totalSugarG.min.toFixed(1)}–{meal.totals.totalSugarG.max.toFixed(1)} 克</b></span><span>游离糖 <b>{meal.totals.freeSugarG.min.toFixed(1)}–{meal.totals.freeSugarG.max.toFixed(1)} 克</b></span></div>
        <div className="meal-detail__actions"><button className="meal-delete-button" type="button" onClick={() => setDeleteMeal(meal)}>删除餐食</button></div>
      </article>)}
    </section>}
    {notice && <div className="notice" role="status">{notice}</div>}
    {deleteMeal && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !deleting) setDeleteMeal(undefined); }}>
      <section className="modal meal-delete-dialog" role="alertdialog" aria-modal="true" aria-labelledby="delete-meal-title" aria-describedby="delete-meal-description">
        <header><div><span className="eyebrow">删除记录</span><h2 id="delete-meal-title">删除这餐？</h2></div><button className="icon-button" type="button" aria-label="关闭" disabled={deleting} onClick={() => setDeleteMeal(undefined)}><Icon name="close" /></button></header>
        <p id="delete-meal-description">删除后，这餐会从今日摄入、营养月历和咨询上下文中移除，本机保存的餐食缩略图也会删除。此操作无法撤销。</p>
        <div className="meal-delete-dialog__actions"><button className="button button--secondary" type="button" disabled={deleting} onClick={() => setDeleteMeal(undefined)}>取消</button><button className="button button--danger" type="button" disabled={deleting} onClick={() => void confirmDeleteMeal()}>{deleting ? '正在删除…' : '确认删除'}</button></div>
      </section>
    </div>}
    {photo && <div className="photo-viewer" role="dialog" aria-modal="true" onClick={() => setPhoto('')}><button className="icon-button" aria-label="关闭"><Icon name="close" /></button><img src={photo} alt="餐食缩略图放大预览" /></div>}
  </section>;
}
