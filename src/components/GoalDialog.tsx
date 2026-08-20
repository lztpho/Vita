// SPDX-License-Identifier: Apache-2.0
import { useState } from 'react';
import type { NutritionGoalProposal, UserProfile } from '../types';
import { VitaNative, errorText } from '../native';
import { Icon } from './Icons';

const emptyProfile: UserProfile = {
  heightCm: 170, birthDate: '', weightKg: 65, equationSex: 'male', activityLevel: 'light', goalType: 'maintenance', generalHealthEligible: false,
};

const labels: Record<keyof NutritionGoalProposal['targets'], string> = {
  caloriesKcal: '热量（千卡）', proteinG: '蛋白质（克）', carbohydrateG: '碳水（克）', fatG: '脂肪（克）', fiberG: '膳食纤维（克）', freeSugarG: '游离糖（克）',
};

export function GoalDialog({ onClose, onConfirmed }: { onClose: () => void; onConfirmed: () => void }) {
  const [profile, setProfile] = useState(emptyProfile);
  const [proposal, setProposal] = useState<NutritionGoalProposal>();
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');

  const update = <K extends keyof UserProfile>(key: K, value: UserProfile[K]) => setProfile((current) => ({ ...current, [key]: value }));

  async function propose() {
    setBusy(true); setNotice('');
    try { setProposal((await VitaNative.createGoalProposal({ profile })).proposal); }
    catch (error) { setNotice(errorText(error)); } finally { setBusy(false); }
  }

  async function confirm() {
    if (!proposal) return;
    setBusy(true); setNotice('');
    try { await VitaNative.confirmGoal({ proposalId: proposal.id, targets: proposal.targets }); onConfirmed(); onClose(); }
    catch (error) { setNotice(errorText(error)); } finally { setBusy(false); }
  }

  return <div className="modal-backdrop"><section className="modal goal-modal" role="dialog" aria-modal="true" aria-labelledby="goal-title">
    <header><div><span className="eyebrow">目标建议</span><h2 id="goal-title">让 Vita 提议营养范围</h2></div><button className="icon-button" onClick={onClose} aria-label="关闭"><Icon name="close" /></button></header>
    {!proposal ? <form className="form-stack" onSubmit={(event) => { event.preventDefault(); void propose(); }}>
      <div className="form-grid">
        <label><span>身高（cm）</span><input type="number" min="120" max="230" value={profile.heightCm} onChange={(e) => update('heightCm', Number(e.target.value))} required /></label>
        <label><span>体重（kg）</span><input type="number" min="30" max="300" step="0.1" value={profile.weightKg} onChange={(e) => update('weightKg', Number(e.target.value))} required /></label>
        <label><span>出生日期</span><input type="date" value={profile.birthDate} max={new Date().toISOString().slice(0, 10)} onChange={(e) => update('birthDate', e.target.value)} required /></label>
        <label><span>能量方程参数</span><select value={profile.equationSex} onChange={(e) => update('equationSex', e.target.value as UserProfile['equationSex'])}><option value="male">男性参数</option><option value="female">女性参数</option></select></label>
        <label><span>日常活动</span><select value={profile.activityLevel} onChange={(e) => update('activityLevel', e.target.value as UserProfile['activityLevel'])}><option value="sedentary">久坐为主</option><option value="light">轻度活动</option><option value="moderate">中等活动</option><option value="high">高活动量</option></select></label>
        <label><span>阶段目标</span><select value={profile.goalType} onChange={(e) => update('goalType', e.target.value as UserProfile['goalType'])}><option value="muscle_gain">增肌</option><option value="fat_loss">减脂</option><option value="recomposition">身体重组</option><option value="maintenance">维持</option></select></label>
      </div>
      <fieldset className="safety-check"><legend>适用范围确认</legend><label><input type="checkbox" required checked={profile.generalHealthEligible} onChange={(e) => update('generalHealthEligible', e.target.checked)} />我已满 18 岁且未满 65 岁，并确认目前没有妊娠或哺乳、饮食障碍、慢性病或持续用药，也没有医生规定的特殊饮食。若有任一情况，我不会使用此功能生成数值目标。</label></fieldset>
      <p className="privacy-note">身高、体重、出生日期和资格确认仅在本机用于本次计算，不发送给云模型，也不保存具体筛查内容。</p>
      <button className="button button--primary button--full" disabled={busy}><Icon name="spark" />{busy ? '正在生成…' : '生成目标建议'}</button>
    </form> : <div className="proposal">
      <p>{proposal.explanation}</p>
      <div className="proposal-grid">{(Object.keys(proposal.targets) as Array<keyof typeof proposal.targets>).map((key) => <label key={key}><span>{labels[key]}</span><div><input type="number" step="0.1" value={proposal.targets[key].min} onChange={(e) => setProposal({ ...proposal, targets: { ...proposal.targets, [key]: { ...proposal.targets[key], min: Number(e.target.value) } } })} /><b>至</b><input type="number" step="0.1" value={proposal.targets[key].max} onChange={(e) => setProposal({ ...proposal, targets: { ...proposal.targets, [key]: { ...proposal.targets[key], max: Number(e.target.value) } } })} /></div></label>)}</div>
      {proposal.warnings.length > 0 && <ul className="warning-list">{proposal.warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul>}
      <p className="privacy-note">确认后生效。健康管理参考，非医学诊断。</p>
      <div className="draft-actions"><button className="button button--secondary" onClick={() => setProposal(undefined)}>返回修改</button><button className="button button--primary" onClick={() => void confirm()} disabled={busy}><Icon name="check" />确认目标</button></div>
    </div>}
    {notice && <div className="notice" role="status">{notice}</div>}
  </section></div>;
}
