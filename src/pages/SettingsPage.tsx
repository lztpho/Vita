// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from 'react';
import type { ModelTestResult, ProviderConfig, ProviderModel } from '../types';
import { VitaNative, errorText } from '../native';
import { Icon } from '../components/Icons';
import { applyProviderPreset, DEFAULT_PROVIDER_ID, detectProvider, normalizeProviderConfig, providerPreset, PROVIDER_PRESETS, type ProviderPresetId } from '../lib/providers';

const blank: ProviderConfig = { protocol: 'openai', baseUrl: '', visionModel: '', textModel: '', hasApiKey: false, configured: false };
const initial = applyProviderPreset(blank, DEFAULT_PROVIDER_ID);
const sponsorUrl = 'https://afdian.com/a/lztpho';
const sourceUrl = 'https://github.com/lztpho/Vita';

function settingsProvider(config?: ProviderConfig): ProviderConfig {
  const normalized = normalizeProviderConfig(config || blank);
  const untouched = !normalized.baseUrl && !normalized.visionModel && !normalized.textModel && !normalized.hasApiKey && !normalized.configured;
  return untouched ? { ...initial } : normalized;
}

export function SettingsPage({ provider, onSaved }: { provider?: ProviderConfig; onSaved: (provider: ProviderConfig) => void }) {
  const [form, setForm] = useState<ProviderConfig>(() => settingsProvider(provider));
  const [selectedProvider, setSelectedProvider] = useState<ProviderPresetId>(() => detectProvider(settingsProvider(provider)));
  const [busy, setBusy] = useState(false);
  const [loadingModels, setLoadingModels] = useState(false);
  const [models, setModels] = useState<ProviderModel[]>([]);
  const [providerPickerOpen, setProviderPickerOpen] = useState(false);
  const [modelPickerOpen, setModelPickerOpen] = useState(false);
  const [modelQuery, setModelQuery] = useState('');
  const [notice, setNotice] = useState('');
  const [dataNotice, setDataNotice] = useState('');
  const [test, setTest] = useState<ModelTestResult>();

  useEffect(() => {
    if (provider) {
      const normalized = settingsProvider(provider);
      setForm(normalized);
      setSelectedProvider(detectProvider(normalized));
    }
  }, [provider]);

  const set = (key: keyof ProviderConfig, value: string) => setForm((current) => ({ ...current, [key]: value }));
  const preset = providerPreset(selectedProvider);

  function chooseProvider(id: ProviderPresetId) {
    setSelectedProvider(id);
    setForm((current) => applyProviderPreset(current, id));
    setTest(undefined);
    setModels([]);
    setProviderPickerOpen(false);
    setModelPickerOpen(false);
    setModelQuery('');
    setNotice('');
  }

  async function loadModels() {
    setLoadingModels(true); setNotice(''); setTest(undefined);
    try {
      const result = await VitaNative.listProviderModels({ protocol: form.protocol, baseUrl: form.baseUrl.trim() });
      setModels(result.models);
      if (result.models.length === 0) {
        setNotice(result.capabilityKnown
          ? '接口返回的模型均未声明图片输入能力，请选择支持图片理解的多模态模型'
          : '没有获取到可确认图片能力的模型，请查阅厂商文档后手动填写多模态模型 ID');
      } else {
        setModelQuery('');
        setModelPickerOpen(true);
        setNotice(result.capabilityKnown
          ? `已获取 ${result.models.length} 个支持图片输入的模型，请选择`
          : `已获取 ${result.models.length} 个模型，但接口未声明图片能力；使用前请确认所选模型支持图片输入`);
      }
    } catch (error) {
      setModels([]);
      setNotice(errorText(error));
    } finally {
      setLoadingModels(false);
    }
  }

  async function save() {
    setBusy(true); setNotice(''); setTest(undefined);
    try {
      const result = await VitaNative.configureProvider({
        protocol: form.protocol,
        baseUrl: form.baseUrl.trim(),
        visionModel: form.visionModel.trim(),
        textModel: '',
      });
      setForm(result.provider); onSaved(result.provider); setNotice('设置已保存');
    } catch (error) { setNotice(errorText(error)); } finally { setBusy(false); }
  }

  async function apiKey(clear = false) {
    setBusy(true); setNotice('');
    try {
      const result = await VitaNative.promptApiKey({ clear, baseUrl: form.baseUrl.trim(), protocol: form.protocol });
      setForm((current) => ({ ...current, baseUrl: result.baseUrl || current.baseUrl, hasApiKey: result.hasApiKey }));
      setNotice(clear ? 'API Key 已清除' : 'API Key 已保存');
    } catch (error) { setNotice(errorText(error)); } finally { setBusy(false); }
  }

  async function runTest() {
    setBusy(true); setNotice(''); setTest(undefined);
    try { setTest(await VitaNative.testProvider()); } catch (error) { setNotice(errorText(error)); } finally { setBusy(false); }
  }

  async function clearAllData() {
    if (!window.confirm('这会永久删除 Vita 中的餐食、聊天、目标、图片缩略图、草稿、模型配置和 API Key。继续吗？')) return;
    if (!window.confirm('最后确认：删除后无法恢复。是否清空全部本地数据？')) return;
    setBusy(true); setDataNotice('');
    try {
      await VitaNative.clearAllLocalData({ confirmed: true });
      setForm(initial); setSelectedProvider(DEFAULT_PROVIDER_ID); setModels([]); setTest(undefined);
      onSaved(initial); setDataNotice('全部本地数据已清空');
    } catch (error) { setDataNotice(errorText(error)); } finally { setBusy(false); }
  }

  async function exportDiagnosticLogs() {
    setBusy(true); setDataNotice('');
    try {
      const result = await VitaNative.exportDiagnosticLogs();
      setDataNotice(result.exported ? '诊断日志已导出' : '已取消导出');
    } catch (error) { setDataNotice(errorText(error)); } finally { setBusy(false); }
  }

  const selectedModel = models.find((model) => model.id === form.visionModel);
  const normalizedModelQuery = modelQuery.trim().toLocaleLowerCase();
  const visibleModels = normalizedModelQuery
    ? models.filter((model) => `${model.name || ''} ${model.id}`.toLocaleLowerCase().includes(normalizedModelQuery))
    : models;

  return (
    <section className="page page--settings">
      <div className="page-heading">
        <h1>模型设置</h1>
      </div>

      <form className="panel form-stack settings-form" onSubmit={(event) => { event.preventDefault(); void save(); }}>
        <div className="settings-field">
          <span>AI 厂商</span>
          <button type="button" className="model-select-button provider-select-button" onClick={() => setProviderPickerOpen(true)} aria-haspopup="dialog">
            <span>{preset.name}</span>
          </button>
        </div>
        <div className="secret-row">
          <div className="secret-row__info"><strong>API Key</strong><small>{form.hasApiKey ? '已填写' : '必填，仅加密保存在本机'}</small></div>
          <div className="secret-row__actions">
            <button type="button" className="button button--secondary" onClick={() => void apiKey(false)} disabled={busy}>{form.hasApiKey ? '更新 Key' : '填写 Key'}</button>
            {form.hasApiKey && <button type="button" className="text-action" onClick={() => void apiKey(true)} disabled={busy}>清除</button>}
          </div>
        </div>
        <div className="model-picker">
          <label>
            <span>多模态模型</span>
            {models.length > 0 ? (
              <button type="button" className={`model-select-button${form.visionModel ? '' : ' is-empty'}`} onClick={() => { setModelQuery(''); setModelPickerOpen(true); }} aria-haspopup="dialog">
                <span>{selectedModel?.name && selectedModel.name !== selectedModel.id ? selectedModel.name : form.visionModel || '请选择模型'}</span>
                <small>{selectedModel?.name && selectedModel.name !== selectedModel.id ? selectedModel.id : ''}</small>
              </button>
            ) : (
              <>
                <input value={form.visionModel} onChange={(event) => set('visionModel', event.target.value)} placeholder={preset.modelPlaceholder} list={preset.suggestedModels?.length ? 'provider-model-suggestions' : undefined} autoCapitalize="none" autoCorrect="off" required />
                {preset.suggestedModels?.length ? <datalist id="provider-model-suggestions">{preset.suggestedModels.map((model) => <option key={model} value={model} />)}</datalist> : null}
              </>
            )}
          </label>
          <button type="button" className="button button--secondary" onClick={() => void loadModels()} disabled={busy || loadingModels || !form.baseUrl.trim()}>{loadingModels ? '获取中' : models.length ? '刷新' : '获取模型'}</button>
        </div>
        {models.length > 0 && <button type="button" className="text-action model-manual" onClick={() => { setModels([]); setModelPickerOpen(false); }}>手动填写模型 ID</button>}
        <details className="advanced-settings" open={selectedProvider === 'custom'}>
          <summary>高级设置</summary>
          <div>
            <label>
              <span>接口协议</span>
              <select value={form.protocol} onChange={(event) => setForm((current) => ({ ...current, protocol: event.target.value as ProviderConfig['protocol'] }))}>
                <option value="openai">OpenAI-compatible</option>
                <option value="anthropic">Anthropic Messages</option>
              </select>
            </label>
            <label>
              <span>API Base URL</span>
              <input type="url" inputMode="url" value={form.baseUrl} onChange={(event) => setForm((current) => ({ ...current, baseUrl: event.target.value, hasApiKey: false }))} placeholder={form.protocol === 'anthropic' ? 'https://api.anthropic.com/v1' : 'https://example.com/v1'} autoCapitalize="none" autoCorrect="off" required />
            </label>
          </div>
        </details>
        <div className="settings-actions">
          <button className="button button--primary" disabled={busy}><Icon name="check" />保存</button>
          <button type="button" className="button button--secondary" onClick={() => void runTest()} disabled={busy || !form.configured || !form.hasApiKey}>测试</button>
        </div>
        {test && <div className="test-result" role="status">
          <span data-status={test.verified === false ? 'pending' : (test.usable ?? (test.vision && test.structured)) ? 'ok' : 'error'}>
            {test.verified === false ? '测试未完成' : (test.usable ?? (test.vision && test.structured)) ? '连接正常' : '连接失败'}
          </span>
          <small>{test.latencyMs} ms · {test.detail}</small>
        </div>}
        {notice && <div className="notice notice--floating settings-notice" role="status" aria-live="polite">{notice}</div>}
      </form>
      <section className="panel form-stack settings-information" aria-labelledby="data-heading">
        <h2 id="data-heading">数据与开源信息</h2>
        <nav className="settings-links" aria-label="项目链接">
          <a className="settings-source-link" href={sourceUrl} target="_blank" rel="noopener noreferrer">GitHub 开源地址</a>
          <a href={`${sourceUrl}/blob/main/PRIVACY.md`} target="_blank" rel="noopener noreferrer">隐私说明</a>
          <a href={`${sourceUrl}/blob/main/THIRD_PARTY_NOTICES.md`} target="_blank" rel="noopener noreferrer">第三方许可</a>
        </nav>
        <div className="settings-data-actions">
          <button type="button" className="button button--secondary" onClick={() => void exportDiagnosticLogs()} disabled={busy}>导出诊断日志</button>
          <button type="button" className="button button--danger" onClick={() => void clearAllData()} disabled={busy}>清空全部本地数据</button>
        </div>
        {dataNotice && <div className="notice" role="status" aria-live="polite">{dataNotice}</div>}
      </section>
      <section className="panel sponsor-card" aria-labelledby="sponsor-heading">
        <div className="sponsor-card__message">
          <h2 id="sponsor-heading">如果觉得有用的话，可以请作者吃一斤无籽西瓜 🍉</h2>
        </div>
        <a className="button sponsor-card__button" href={sponsorUrl} target="_blank" rel="noopener noreferrer">点击请作者吃瓜</a>
      </section>
      {providerPickerOpen && <div className="modal-backdrop model-picker-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setProviderPickerOpen(false); }}>
        <section className="modal model-picker-dialog provider-picker-dialog" role="dialog" aria-modal="true" aria-labelledby="provider-picker-title">
          <header>
            <div>
              <h2 id="provider-picker-title">选择 AI 厂商</h2>
              <small>{PROVIDER_PRESETS.length} 个可选厂商</small>
            </div>
            <button type="button" className="icon-button" aria-label="关闭厂商选择" onClick={() => setProviderPickerOpen(false)}><Icon name="close" /></button>
          </header>
          <div className="model-option-list provider-option-list" role="listbox" aria-label="可选 AI 厂商">
            {PROVIDER_PRESETS.map((item) => {
              const selected = item.id === selectedProvider;
              return <button key={item.id} type="button" className={`model-option provider-option${selected ? ' is-selected' : ''}`} role="option" aria-selected={selected} onClick={() => chooseProvider(item.id)}>
                <span><strong>{item.name}</strong></span>
                <i aria-hidden="true">{selected && <Icon name="check" />}</i>
              </button>;
            })}
          </div>
        </section>
      </div>}
      {modelPickerOpen && <div className="modal-backdrop model-picker-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setModelPickerOpen(false); }}>
        <section className="modal model-picker-dialog" role="dialog" aria-modal="true" aria-labelledby="model-picker-title">
          <header>
            <div>
              <h2 id="model-picker-title">选择模型</h2>
              <small>{models.length} 个可选模型</small>
            </div>
            <button type="button" className="icon-button" aria-label="关闭模型选择" onClick={() => setModelPickerOpen(false)}><Icon name="close" /></button>
          </header>
          <label className="model-picker-search">
            <span className="sr-only">搜索模型</span>
            <input type="search" value={modelQuery} onChange={(event) => setModelQuery(event.target.value)} placeholder="搜索模型名称或 ID" autoFocus />
          </label>
          <div className="model-option-list" role="listbox" aria-label="可选模型">
            {visibleModels.map((model) => {
              const selected = model.id === form.visionModel;
              return <button key={model.id} type="button" className={`model-option${selected ? ' is-selected' : ''}`} role="option" aria-selected={selected} onClick={() => { set('visionModel', model.id); setModelPickerOpen(false); }}>
                <span>
                  <strong>{model.name || model.id}</strong>
                  {model.name && model.name !== model.id && <small>{model.id}</small>}
                </span>
                <i aria-hidden="true">{selected && <Icon name="check" />}</i>
              </button>;
            })}
            {visibleModels.length === 0 && <div className="model-option-empty">没有匹配的模型</div>}
          </div>
        </section>
      </div>}
    </section>
  );
}
