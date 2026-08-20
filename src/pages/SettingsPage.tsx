// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from 'react';
import type { ModelTestResult, ProviderConfig, ProviderModel } from '../types';
import { VitaNative, errorText } from '../native';
import { Icon } from '../components/Icons';
import { applyProviderPreset, detectProvider, normalizeProviderConfig, providerPreset, PROVIDER_PRESETS, type ProviderPresetId } from '../lib/providers';

const initial: ProviderConfig = { protocol: 'openai', baseUrl: '', visionModel: '', textModel: '', hasApiKey: false, configured: false };

export function SettingsPage({ provider, onSaved }: { provider?: ProviderConfig; onSaved: (provider: ProviderConfig) => void }) {
  const [form, setForm] = useState<ProviderConfig>(() => normalizeProviderConfig(provider || initial));
  const [selectedProvider, setSelectedProvider] = useState<ProviderPresetId>(() => detectProvider(provider));
  const [busy, setBusy] = useState(false);
  const [loadingModels, setLoadingModels] = useState(false);
  const [models, setModels] = useState<ProviderModel[]>([]);
  const [notice, setNotice] = useState('');
  const [test, setTest] = useState<ModelTestResult>();

  useEffect(() => {
    if (provider) {
      setForm(normalizeProviderConfig(provider));
      setSelectedProvider(detectProvider(provider));
    }
  }, [provider]);

  const set = (key: keyof ProviderConfig, value: string) => setForm((current) => ({ ...current, [key]: value }));
  const preset = providerPreset(selectedProvider);

  function chooseProvider(id: ProviderPresetId) {
    setSelectedProvider(id);
    setForm((current) => applyProviderPreset(current, id));
    setTest(undefined);
    setModels([]);
    setNotice('');
  }

  async function loadModels() {
    setLoadingModels(true); setNotice(''); setTest(undefined);
    try {
      const result = await VitaNative.listProviderModels({ protocol: form.protocol, baseUrl: form.baseUrl.trim() });
      setModels(result.models);
      if (result.models.length === 0) {
        setNotice('没有获取到可选模型，请手动填写模型 ID');
      } else {
        setNotice(`已获取 ${result.models.length} 个模型，请选择`);
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
      const result = await VitaNative.promptApiKey({ clear, baseUrl: form.baseUrl.trim() });
      setForm((current) => ({ ...current, hasApiKey: result.hasApiKey }));
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
    setBusy(true); setNotice('');
    try {
      await VitaNative.clearAllLocalData({ confirmed: true });
      setForm(initial); setSelectedProvider('openai'); setModels([]); setTest(undefined);
      onSaved(initial); setNotice('全部本地数据已清空');
    } catch (error) { setNotice(errorText(error)); } finally { setBusy(false); }
  }

  return (
    <section className="page page--settings">
      <div className="page-heading">
        <h1>模型设置</h1>
      </div>

      <form className="panel form-stack" onSubmit={(event) => { event.preventDefault(); void save(); }}>
        <label>
          <span>AI 厂商</span>
          <select value={selectedProvider} onChange={(event) => chooseProvider(event.target.value as ProviderPresetId)}>
            {PROVIDER_PRESETS.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
        </label>
        {preset.statusNote && <p className="provider-note" role="note">{preset.statusNote}</p>}
        <div className="secret-row">
          <div><strong>API Key</strong><small>{form.hasApiKey ? '已填写' : '必填，仅加密保存在本机'}</small></div>
          <button type="button" className="button button--secondary" onClick={() => void apiKey(false)} disabled={busy || !form.configured}>{form.hasApiKey ? '更新 Key' : '填写 Key'}</button>
          {form.hasApiKey && <button type="button" className="text-action" onClick={() => void apiKey(true)} disabled={busy}>清除</button>}
        </div>
        <div className="model-picker">
          <label>
            <span>多模态模型</span>
            {models.length > 0 ? (
              <select value={form.visionModel} onChange={(event) => set('visionModel', event.target.value)} required>
                <option value="">请选择模型</option>
                {!models.some((model) => model.id === form.visionModel) && form.visionModel && <option value={form.visionModel}>{form.visionModel}</option>}
                {models.map((model) => <option key={model.id} value={model.id}>{model.name && model.name !== model.id ? `${model.name} · ${model.id}` : model.id}</option>)}
              </select>
            ) : (
              <>
                <input value={form.visionModel} onChange={(event) => set('visionModel', event.target.value)} placeholder={preset.modelPlaceholder} list={preset.suggestedModels?.length ? 'provider-model-suggestions' : undefined} autoCapitalize="none" autoCorrect="off" required />
                {preset.suggestedModels?.length ? <datalist id="provider-model-suggestions">{preset.suggestedModels.map((model) => <option key={model} value={model} />)}</datalist> : null}
              </>
            )}
          </label>
          <button type="button" className="button button--secondary" onClick={() => void loadModels()} disabled={busy || loadingModels || !form.baseUrl.trim()}>{loadingModels ? '获取中' : models.length ? '刷新' : '获取模型'}</button>
        </div>
        {models.length > 0 && <button type="button" className="text-action model-manual" onClick={() => setModels([])}>手动填写模型 ID</button>}
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
        {notice && <div className="notice" role="status">{notice}</div>}
      </form>
      <section className="panel form-stack settings-information" aria-labelledby="data-heading">
        <h2 id="data-heading">数据与开源信息</h2>
        <p>餐食图片、备注和咨询内容会发送给你配置的云模型厂商。图片会先在本机移除 EXIF/GPS 并重新编码；Vita 不包含遥测。</p>
        <p><a href="https://github.com/lztpho/Vita/blob/main/PRIVACY.md" target="_blank" rel="noopener noreferrer">隐私说明</a> · <a href="https://github.com/lztpho/Vita/blob/main/THIRD_PARTY_NOTICES.md" target="_blank" rel="noopener noreferrer">第三方许可</a> · <a href="https://github.com/lztpho/Vita" target="_blank" rel="noopener noreferrer">源代码</a></p>
        <button type="button" className="button button--danger" onClick={() => void clearAllData()} disabled={busy}>清空全部本地数据</button>
      </section>
    </section>
  );
}
