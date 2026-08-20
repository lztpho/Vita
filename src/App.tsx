// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from 'react';
import type { AppState } from './types';
import { VitaNative, errorText } from './native';
import { Layout, type ViewName } from './components/Layout';
import { CapturePage } from './pages/CapturePage';
import { TodayPage } from './pages/TodayPage';
import { CalendarPage } from './pages/CalendarPage';
import { ChatPage } from './pages/ChatPage';
import { SettingsPage } from './pages/SettingsPage';
import { BrandMark } from './components/Icons';

export function App() {
  const [state, setState] = useState<AppState>();
  const [view, setView] = useState<ViewName>('capture');
  const [refreshKey, setRefreshKey] = useState(0);
  const [newChatRequest, setNewChatRequest] = useState(0);
  const [error, setError] = useState('');

  async function load() {
    try {
      const next = await VitaNative.getAppState(); setState(next); setError('');
      if (!next.provider.configured) setView('settings');
    } catch (reason) { setError(errorText(reason)); }
  }

  async function refreshNutrition() {
    try { const result = await VitaNative.getTodayNutrition(); setState((current) => current ? { ...current, today: result.summary, currentGoal: result.summary.goal } : current); setRefreshKey((value) => value + 1); }
    catch (reason) { setError(errorText(reason)); }
  }

  useEffect(() => { void load(); }, []);
  useEffect(() => { window.scrollTo(0, 0); }, [view]);

  if (!state && !error) return <div className="splash"><BrandMark /><strong>Vita</strong><span>正在打开…</span></div>;
  if (!state) return <div className="fatal"><BrandMark /><h1>暂时无法打开</h1><p>{error}</p><button className="button button--primary" onClick={() => void load()}>重试</button></div>;

  return <Layout view={view} onView={setView} onNewConversation={() => setNewChatRequest((value) => value + 1)}>
    {view === 'capture' && <CapturePage onConfirmed={() => void refreshNutrition()} />}
    {view === 'today' && <TodayPage summary={state.today} onRefresh={() => void refreshNutrition()} />}
    {view === 'calendar' && <CalendarPage refreshKey={refreshKey} />}
    {view === 'chat' && <ChatPage newSessionRequest={newChatRequest} />}
    {view === 'settings' && <SettingsPage provider={state.provider} onSaved={(provider) => setState({ ...state, provider })} />}
    {error && <div className="notice notice--floating" role="alert">{error}</div>}
  </Layout>;
}
