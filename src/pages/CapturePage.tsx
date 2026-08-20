// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useRef, useState } from 'react';
import { Camera, CameraResultType, CameraSource } from '@capacitor/camera';
import { Capacitor } from '@capacitor/core';
import type { MealDraft, MealItem, MealTemplate, Nutrients, Range, SelectedImage } from '../types';
import { VitaNative, errorText, type MealAnalysisTask } from '../native';
import { Icon } from '../components/Icons';

const multipliers = [0.5, 0.75, 1, 1.25, 1.5];
const nutrientFields: Array<{ key: keyof Nutrients; label: string; unit: string; digits: number }> = [
  { key: 'caloriesKcal', label: '热量', unit: '千卡', digits: 0 },
  { key: 'proteinG', label: '蛋白质', unit: '克', digits: 1 },
  { key: 'carbohydrateG', label: '碳水', unit: '克', digits: 1 },
  { key: 'fatG', label: '脂肪', unit: '克', digits: 1 },
  { key: 'fiberG', label: '膳食纤维', unit: '克', digits: 1 },
  { key: 'totalSugarG', label: '总糖', unit: '克', digits: 1 },
  { key: 'freeSugarG', label: '游离糖', unit: '克', digits: 1 },
];

function localInputValue(time = Date.now()) {
  const date = new Date(time - new Date(time).getTimezoneOffset() * 60_000);
  return date.toISOString().slice(0, 16);
}

function rangeText(value: Range, unit: string, digits = 1) {
  return `${value.min.toFixed(digits)}–${value.max.toFixed(digits)} ${unit}`;
}

function confidenceText(value?: MealDraft['confidence']) {
  return value === 'high' ? '识别把握较高' : value === 'low' ? '部分内容需核对' : '识别把握一般';
}

function itemNutrients(item: MealItem) {
  return [
    rangeText(item.nutrients.caloriesKcal, '千卡', 0),
    `蛋白质 ${rangeText(item.nutrients.proteinG, '克')}`,
    `碳水 ${rangeText(item.nutrients.carbohydrateG, '克')}`,
    `脂肪 ${rangeText(item.nutrients.fatG, '克')}`,
  ].join(' · ');
}

export function RecognitionDetails({ items, assumptions = [] }: { items: MealItem[]; assumptions?: string[] }) {
  return <>
    <ul className="meal-food-list">{items.filter((item) => !item.removed).map((item) => <li key={item.id}>
      <strong>{item.name}</strong>
      <span>{item.amountLabel} · {rangeText(item.nutrients.caloriesKcal, '千卡', 0)}</span>
    </li>)}</ul>
    {assumptions.length ? <section className="analysis-assumptions"><strong>估算依据</strong><p>{assumptions.join('；')}</p></section> : null}
  </>;
}

function wait(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function AnalysisProgress({ task }: { task: MealAnalysisTask }) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const total = Math.max(1, task.totalImages || 1);
  const processed = Math.min(total, Math.max(0, task.processedImages || 0));
  const upload = Math.min(100, Math.max(0, task.uploadPercent || 0));
  const phase = task.phase || 'prepare';
  const percent = phase === 'prepare' ? 4 + (processed / total) * 20
    : phase === 'upload' ? 24 + upload * 0.42
      : phase === 'model' ? undefined
        : phase === 'validate' ? 92 : phase === 'done' ? 100 : 2;
  const message = phase === 'prepare' ? `正在准备照片 ${processed + (processed < total ? 1 : 0)}/${total}`
    : phase === 'upload' ? `正在上传照片 ${upload}%`
      : phase === 'model' ? 'AI 正在识别整餐'
        : phase === 'validate' ? '正在整理结果'
          : '正在开始分析';
  const elapsedSeconds = Math.max(0, Math.round((now - Number(task.startedAt || now)) / 1000));
  const elapsed = elapsedSeconds < 60 ? `${elapsedSeconds} 秒` : `${Math.floor(elapsedSeconds / 60)} 分 ${String(elapsedSeconds % 60).padStart(2, '0')} 秒`;
  const stages = [
    ['prepare', '处理照片'], ['upload', '发送照片'], ['model', 'AI 识别'], ['validate', '校验结果'],
  ];
  const order = ['prepare', 'upload', 'model', 'validate', 'done'];
  const current = order.indexOf(phase);
  return <section className="analysis-task panel" aria-live="polite">
    <span className="eyebrow">餐食分析</span>
    <h2>{message}</h2>
    <div className={`analysis-task__track ${percent == null ? 'is-indeterminate' : ''}`} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent == null ? undefined : Math.round(percent)}><span style={{ width: percent == null ? '34%' : `${percent}%` }} /></div>
    <ol>{stages.map(([key, label]) => <li key={key} data-state={order.indexOf(key) < current ? 'complete' : key === phase ? 'current' : 'pending'}>{label}</li>)}</ol>
    <p>{message} · 已用时 {elapsed}</p>
  </section>;
}

function TemplateThumbnail({ meal }: { meal: MealTemplate }) {
  const [url, setUrl] = useState('');
  useEffect(() => {
    let alive = true;
    if (meal.thumbnailCount > 0) {
      void VitaNative.getMealThumbnail({ mealId: meal.mealId, index: 0 })
        .then((result) => { if (alive) setUrl(result.dataUrl); })
        .catch(() => undefined);
    }
    return () => { alive = false; };
  }, [meal.mealId, meal.thumbnailCount]);
  return <div className="template-thumb">{url ? <img src={url} alt="历史餐食参考图" /> : <Icon name="image" />}</div>;
}

export function MealCorrection({ conversation, busy, onRefine }: {
  conversation?: MealDraft['correctionConversation'];
  busy: boolean;
  onRefine: (message: string) => Promise<boolean>;
}) {
  const inputRef = useRef<HTMLTextAreaElement>(null);

  async function submit() {
    const message = inputRef.current?.value.trim() || '';
    if (!message || busy) return;
    if (await onRefine(message)) {
      if (inputRef.current) inputRef.current.value = '';
    }
  }

  return <section className="meal-correction">
    <h3>修正识别</h3>
    {conversation?.length ? <div className="correction-thread">{conversation.map((message, index) => <p data-role={message.role} key={`${message.role}-${index}`}>{message.content}</p>)}</div> : null}
    <form onSubmit={(event) => { event.preventDefault(); void submit(); }}><textarea ref={inputRef} rows={2} maxLength={500} required placeholder="例如：只有两个鸡腿，鸡皮没有吃" aria-label="告诉 Vita 如何修正这餐" /><button className="button button--secondary" disabled={busy}>{busy ? '正在修正…' : '发送修正'}</button></form>
  </section>;
}

export function CapturePage({ onConfirmed }: { onConfirmed: () => void }) {
  const [images, setImages] = useState<SelectedImage[]>([]);
  const [notes, setNotes] = useState('');
  const [isBackfill, setBackfill] = useState(false);
  const [consumedAt, setConsumedAt] = useState(localInputValue());
  const [draft, setDraft] = useState<MealDraft>();
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [historyOpen, setHistoryOpen] = useState(false);
  const [templates, setTemplates] = useState<MealTemplate[]>([]);
  const [query, setQuery] = useState('');
  const [referencePhotos, setReferencePhotos] = useState<string[]>([]);
  const [photo, setPhoto] = useState('');
  const [analysisTask, setAnalysisTask] = useState<MealAnalysisTask>();
  const fileRef = useRef<HTMLInputElement>(null);
  const imagesRef = useRef<SelectedImage[]>([]);
  const mountedRef = useRef(true);
  const pollingTaskRef = useRef('');

  useEffect(() => { imagesRef.current = images; }, [images]);
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      imagesRef.current.forEach((image) => { if (image.previewUrl.startsWith('blob:')) URL.revokeObjectURL(image.previewUrl); });
    };
  }, []);

  useEffect(() => {
    if (typeof VitaNative.getLatestMealAnalysisTask !== 'function') return;
    void VitaNative.getLatestMealAnalysisTask().then((task) => {
      const recent = Date.now() - Number(task.startedAt || 0) < 6 * 60 * 60 * 1000;
      if (recent && task.taskId && task.status !== 'none') void pollAnalysis(task.taskId);
    }).catch(() => undefined);
  }, []);

  useEffect(() => {
    let alive = true;
    setReferencePhotos([]);
    if (draft?.recordingMethod === 'historical_reuse' && draft.sourceMealId && (draft.referenceThumbnailCount || 0) > 0) {
      void Promise.all(Array.from({ length: draft.referenceThumbnailCount || 0 }, (_, index) =>
        VitaNative.getMealThumbnail({ mealId: draft.sourceMealId!, index }).then((result) => result.dataUrl),
      )).then((urls) => { if (alive) setReferencePhotos(urls); }).catch(() => undefined);
    }
    return () => { alive = false; };
  }, [draft?.id, draft?.recordingMethod, draft?.sourceMealId, draft?.referenceThumbnailCount]);

  const draftPhotos = useMemo(
    () => draft?.recordingMethod === 'historical_reuse' ? referencePhotos : images.map((image) => image.previewUrl),
    [draft?.recordingMethod, images, referencePhotos],
  );

  function addImage(next: SelectedImage) {
    setImages((current) => current.length >= 4 ? current : [...current, next]);
  }

  function removeImage(id: string) {
    setImages((current) => {
      const removed = current.find((image) => image.id === id);
      if (removed?.previewUrl.startsWith('blob:')) URL.revokeObjectURL(removed.previewUrl);
      return current.filter((image) => image.id !== id);
    });
  }

  function clearImages() {
    setImages((current) => {
      current.forEach((image) => { if (image.previewUrl.startsWith('blob:')) URL.revokeObjectURL(image.previewUrl); });
      return [];
    });
  }

  async function camera() {
    setNotice('');
    try {
      const shot = await Camera.getPhoto({ source: CameraSource.Camera, resultType: CameraResultType.Uri, quality: 100, correctOrientation: false, saveToGallery: false });
      addImage({ id: crypto.randomUUID(), uri: shot.path || shot.webPath, previewUrl: shot.webPath || '', name: `camera-${Date.now()}.${shot.format}` });
    } catch (error) { if (!String(error).toLowerCase().includes('cancel')) setNotice(errorText(error)); }
  }

  async function gallery() {
    setNotice('');
    if (!Capacitor.isNativePlatform()) { fileRef.current?.click(); return; }
    try {
      const result = await Camera.pickImages({ quality: 100, limit: Math.max(1, 4 - images.length) });
      result.photos.forEach((picked, index) => addImage({ id: crypto.randomUUID(), uri: picked.path || picked.webPath, previewUrl: picked.webPath, name: `gallery-${Date.now()}-${index}.${picked.format}` }));
    } catch (error) { if (!String(error).toLowerCase().includes('cancel')) setNotice(errorText(error)); }
  }

  async function webFiles(files: FileList | null) {
    if (!files) return;
    for (const file of Array.from(files).slice(0, 4 - images.length)) {
      const dataUrl = await new Promise<string>((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.onerror = reject; reader.readAsDataURL(file); });
      addImage({ id: crypto.randomUUID(), dataUrl, previewUrl: URL.createObjectURL(file), name: file.name });
    }
  }

  async function analyze() {
    if (!images.length) { setNotice('请先拍照或选择图片。'); return; }
    setBusy(true); setNotice('');
    try {
      const started = await VitaNative.startMealAnalysis({ images: images.map(({ uri, dataUrl, name }) => ({ uri, dataUrl, name })), consumedAtMs: new Date(consumedAt).getTime(), notes: notes.trim() });
      setAnalysisTask({ taskId: started.taskId, draftId: '', status: 'queued', phase: 'prepare', startedAt: Date.now(), totalImages: images.length, processedImages: 0, uploadPercent: 0 });
      await pollAnalysis(started.taskId);
    } catch (error) { setNotice(errorText(error)); } finally { setBusy(false); }
  }

  async function pollAnalysis(taskId: string) {
    if (!taskId || pollingTaskRef.current === taskId) return;
    pollingTaskRef.current = taskId;
    setBusy(true);
    try {
      while (mountedRef.current) {
        const task = await VitaNative.getMealAnalysisTask({ taskId });
        if (!mountedRef.current) return;
        setAnalysisTask(task);
        if (task.status === 'succeeded') {
          if (!task.draft) throw new Error('分析完成，但餐食草稿无法读取');
          setDraft(task.draft);
          setAnalysisTask(undefined);
          await VitaNative.forgetMealAnalysisTask({ taskId }).catch(() => undefined);
          return;
        }
        if (task.status === 'failed') throw new Error(task.message || '餐食分析没有完成');
        await wait(800);
      }
    } catch (error) {
      if (mountedRef.current) {
        setAnalysisTask(undefined);
        setNotice(errorText(error));
      }
      await VitaNative.forgetMealAnalysisTask({ taskId }).catch(() => undefined);
    } finally {
      pollingTaskRef.current = '';
      if (mountedRef.current) setBusy(false);
    }
  }

  async function searchHistory(nextQuery = query) {
    setBusy(true); setNotice('');
    try { setTemplates((await VitaNative.listMealTemplates({ days: 90, limit: 30, query: nextQuery.trim() })).meals); setHistoryOpen(true); }
    catch (error) { setNotice(errorText(error)); } finally { setBusy(false); }
  }

  async function reuse(template: MealTemplate) {
    setBusy(true); setNotice('');
    try {
      const result = await VitaNative.createHistoricalReuseDraft({ mealId: template.mealId, revision: template.revision, consumedAtMs: new Date(consumedAt).getTime() });
      setDraft(result.draft); setHistoryOpen(false);
    } catch (error) { setNotice(errorText(error)); } finally { setBusy(false); }
  }

  async function patchDraft(changes: Omit<Parameters<typeof VitaNative.updateMealDraft>[0], 'draftId'>) {
    if (!draft) return;
    setBusy(true); setNotice('');
    try { setDraft((await VitaNative.updateMealDraft({ draftId: draft.id, ...changes })).draft); }
    catch (error) { setNotice(errorText(error)); } finally { setBusy(false); }
  }

  async function refine(message: string) {
    if (!draft || !message) return false;
    setBusy(true); setNotice('');
    try {
      setDraft((await VitaNative.refineMealDraft({ draftId: draft.id, message })).draft);
      return true;
    } catch (error) { setNotice(errorText(error)); return false; } finally { setBusy(false); }
  }

  async function confirm() {
    if (!draft) return;
    setBusy(true); setNotice('');
    try {
      await VitaNative.confirmMealDraft({ draftId: draft.id });
      setDraft(undefined); clearImages(); setNotes(''); setBackfill(false); setConsumedAt(localInputValue());
      setNotice('这一餐已记录'); onConfirmed();
    } catch (error) { setNotice(errorText(error)); } finally { setBusy(false); }
  }

  async function discard() {
    if (draft) await VitaNative.cancelMealDraft({ draftId: draft.id }).catch(() => undefined);
    setDraft(undefined); clearImages(); setNotes(''); setNotice('');
  }

  return (
    <section className="page page--capture">
      <h1 className="sr-only">拍餐</h1>
      <div className="workflow-grid">
      <div className="capture-card panel">
        <div className="dropzone">
          <span className="dropzone-icon"><Icon name="camera" /></span>
          <strong>添加照片</strong>
          <span>最多 4 张</span>
          <div className="source-actions">
          <button className="source-button source-button--primary" onClick={() => void camera()}><Icon name="camera" /><span><strong>拍照</strong><small>现场记录</small></span></button>
          <button className="source-button" onClick={() => void gallery()}><Icon name="image" /><span><strong>相册</strong><small>最多 4 张</small></span></button>
          <input ref={fileRef} type="file" accept="image/*" multiple hidden onChange={(event) => void webFiles(event.target.files)} />
          </div>
        </div>

        <div className="meal-entry-options">
          <label className="meal-backfill-toggle"><input type="checkbox" checked={isBackfill} onChange={(event) => setBackfill(event.target.checked)} /><strong>补录餐食</strong></label>
          <button className="button button--secondary" onClick={() => void searchHistory()}><Icon name="history" />从历史选择</button>
        </div>
        {isBackfill && <label><span>实际用餐时间</span><input type="datetime-local" value={consumedAt} max={localInputValue()} min={localInputValue(Date.now() - 7 * 86_400_000)} onChange={(event) => setConsumedAt(event.target.value)} /></label>}

        {images.length > 0 && <div className="selected-images">
          <div className="section-line"><strong>已选 {images.length}/4 张</strong><button className="text-action" onClick={clearImages}>全部移除</button></div>
          <div className="image-strip">{images.map((image) => <figure key={image.id}><button className="image-preview-button" onClick={() => setPhoto(image.previewUrl)} aria-label="放大查看待分析餐食"><img src={image.previewUrl} alt="待分析餐食" /></button><button className="image-remove-button" onClick={() => removeImage(image.id)} aria-label="移除图片"><Icon name="close" /></button></figure>)}</div>
        </div>}

        <label><span>份量补充（可选）</span><textarea rows={2} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="例如：米饭半碗，鸡肉没有吃皮" /></label>
        <button className="button button--primary button--full" onClick={() => void analyze()} disabled={busy || !images.length}><Icon name="spark" />{busy ? '正在分析…' : '开始分析'}</button>
      </div>

      {analysisTask && <AnalysisProgress task={analysisTask} />}

      {draft && <div className="draft draft-card panel">
        <div className="draft__heading"><div><span className="eyebrow">确认前可调整</span><h2>{draft.recordingMethod === 'historical_reuse' ? '历史餐食复用' : '本餐识别结果'}</h2><small>{confidenceText(draft.confidence)}</small></div><button className="icon-button" onClick={() => void discard()} aria-label="放弃草稿"><Icon name="close" /></button></div>

        <div className="form-grid draft-timing">
          <label><span>实际时间</span><input type="datetime-local" value={localInputValue(draft.consumedAtMs)} max={localInputValue()} min={localInputValue(Date.now() - 7 * 86_400_000)} onChange={(event) => void patchDraft({ consumedAtMs: new Date(event.target.value).getTime() })} /></label>
          <label><span>餐次</span><select value={draft.mealType} onChange={(event) => void patchDraft({ mealType: event.target.value })}><option value="breakfast">早餐</option><option value="lunch">午餐</option><option value="dinner">晚餐</option><option value="snack">加餐</option><option value="late_night">夜宵</option></select></label>
          <p>{draft.recordingMethod === 'historical_reuse' ? '从历史餐食生成；可修改本次实际时间和餐次。' : `按实际进餐时间记录 · 合并分析 ${draftPhotos.length || 1} 张照片`}</p>
        </div>

        {draftPhotos.length > 0 && <section className="draft-photos" aria-label={draft.recordingMethod === 'historical_reuse' ? '历史参考照片' : '本餐照片'}>
          <div>{draftPhotos.map((url, index) => <button key={`${url.slice(-24)}-${index}`} onClick={() => setPhoto(url)} aria-label={`放大查看照片 ${index + 1}`}><img src={url} alt={`餐食照片 ${index + 1}`} /></button>)}</div>
        </section>}

        <div className="draft-summary-grid">{nutrientFields.map(({ key, label, unit, digits }) => <div key={key}><span>{label}</span><strong>{rangeText(draft.totals[key], unit, digits)}</strong></div>)}</div>

        {(draft.nutritionSummary || draft.nutritionHighlights?.length || draft.nutritionAttention?.length) && <section className="nutrition-insights">
          <h3>整餐营养解读</h3>
          {draft.nutritionSummary && <p>{draft.nutritionSummary}</p>}
          <div>{draft.nutritionHighlights?.length ? <article data-kind="good"><strong>营养亮点</strong><ul>{draft.nutritionHighlights.map((item) => <li key={item}>{item}</li>)}</ul></article> : null}{draft.nutritionAttention?.length ? <article data-kind="attention"><strong>需要关注</strong><ul>{draft.nutritionAttention.map((item) => <li key={item}>{item}</li>)}</ul></article> : null}</div>
        </section>}

        <details className="meal-details" open={draft.recordingMethod === 'historical_reuse'}>
          <summary>查看识别明细（{draft.items.filter((item) => !item.removed).length} 项食物）</summary>
          {draft.recordingMethod === 'photo_analysis' ? <RecognitionDetails items={draft.items} assumptions={draft.assumptions} /> : <section className="draft-foods">
          <div className="food-list">{draft.items.map((item) => <article className={item.removed ? 'is-removed' : ''} key={item.id}>
            <div className="food-list__heading"><div><strong>{item.name}</strong><small>{item.amountLabel}</small></div><button className="text-action" onClick={() => void patchDraft({ removedItemIds: item.removed ? draft.items.filter((entry) => entry.removed && entry.id !== item.id).map((entry) => entry.id) : [...draft.items.filter((entry) => entry.removed).map((entry) => entry.id), item.id] })}>{item.removed ? '恢复' : '移除'}</button></div>
            <p>{itemNutrients(item)}</p>
            <label className="item-multiplier"><span>单项份量</span><select value={item.multiplier} disabled={item.removed} onChange={(event) => void patchDraft({ itemMultipliers: { [item.id]: Number(event.target.value) } })}>{multipliers.map((value) => <option key={value} value={value}>{value}×</option>)}</select></label>
            {item.assumptions?.length ? <small className="item-assumption">{item.assumptions.join('；')}</small> : null}
          </article>)}</div>
          {draft.assumptions?.length ? <section className="analysis-assumptions"><strong>估算依据</strong><p>{draft.assumptions.join('；')}</p></section> : null}
          </section>}
        </details>

        {draft.recordingMethod === 'historical_reuse' && <div className="portion-control"><strong>调整本次份量</strong><div>{multipliers.map((value) => <button key={value} className={draft.overallMultiplier === value ? 'is-active' : ''} onClick={() => void patchDraft({ overallMultiplier: value })}>{value}×</button>)}</div></div>}

        {draft.recordingMethod === 'photo_analysis' && <MealCorrection conversation={draft.correctionConversation} busy={busy} onRefine={refine} />}

        <div className="draft-actions"><button className="button button--secondary" onClick={() => void discard()}>放弃</button><button className="button button--primary" onClick={() => void confirm()} disabled={busy}><Icon name="check" />确认记录</button></div>
      </div>}

      {notice && <div className="notice" role="status">{notice}</div>}

      {historyOpen && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setHistoryOpen(false); }}>
        <section className="modal sheet" role="dialog" aria-modal="true" aria-labelledby="history-title">
          <header><div><span className="eyebrow">最近 90 天</span><h2 id="history-title">{isBackfill ? '选择要补录的一餐' : '选择吃过的一餐'}</h2></div><button className="icon-button" onClick={() => setHistoryOpen(false)} aria-label="关闭"><Icon name="close" /></button></header>
          <form className="search-row" onSubmit={(event) => { event.preventDefault(); void searchHistory(); }}><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索食物名称" /><button className="button button--secondary">搜索</button></form>
          <div className="template-list">{templates.length ? templates.map((meal) => <button key={`${meal.mealId}:${meal.revision}`} onClick={() => void reuse(meal)}><TemplateThumbnail meal={meal} /><div><strong>{meal.summary}</strong><small>{new Date(meal.consumedAtMs).toLocaleDateString('zh-CN')} · {meal.items.length} 项食物</small><span>{meal.caloriesKcal.min.toFixed(0)}–{meal.caloriesKcal.max.toFixed(0)} 千卡 · 蛋白质 {meal.proteinG.min.toFixed(1)}–{meal.proteinG.max.toFixed(1)} 克</span></div></button>) : <div className="empty-compact">没有找到可复用的餐食</div>}</div>
        </section>
      </div>}

      {photo && <div className="photo-viewer" role="dialog" aria-modal="true" onClick={() => setPhoto('')}><button className="icon-button" aria-label="关闭"><Icon name="close" /></button><img src={photo} alt="餐食照片放大预览" /></div>}
      </div>
    </section>
  );
}
