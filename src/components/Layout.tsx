// SPDX-License-Identifier: Apache-2.0
import type { ReactNode } from 'react';
import { BrandMark, Icon, type IconName } from './Icons';

export type ViewName = 'today' | 'chat' | 'capture' | 'calendar' | 'settings';

const tabs: Array<{ id: Exclude<ViewName, 'settings'>; label: string; icon: IconName }> = [
  { id: 'today', label: '今日', icon: 'today' },
  { id: 'chat', label: '咨询', icon: 'chat' },
  { id: 'capture', label: '拍餐', icon: 'camera' },
  { id: 'calendar', label: '饮食记录', icon: 'calendar' },
];

export function Layout({ view, onView, onNewConversation, children }: { view: ViewName; onView: (view: ViewName) => void; onNewConversation?: () => void; children: ReactNode }) {
  return (
    <div className="app-shell">
      <header className="app-header">
        <button className="brand" onClick={() => onView('capture')} aria-label="回到拍餐">
          <BrandMark className="brand__mark" />
          <span><strong>Vita</strong></span>
        </button>
        <nav className="desktop-nav" aria-label="主要页面">
          {tabs.map((tab) => <button key={tab.id} className={view === tab.id ? 'is-active' : ''} onClick={() => onView(tab.id)}>{tab.label}</button>)}
        </nav>
        <div className="app-header__actions">
          {view === 'chat' && <button className="button button--secondary header-new-chat" onClick={onNewConversation}>新会话</button>}
          <button className={`icon-button ${view === 'settings' ? 'is-active' : ''}`} onClick={() => onView('settings')} aria-label="设置">
            <Icon name="settings" />
          </button>
        </div>
      </header>
      <main className="app-main" data-view={view}>{children}</main>
      <nav className="bottom-nav" aria-label="主要页面">
        {tabs.map((tab) => (
          <button key={tab.id} className={`${view === tab.id ? 'is-active' : ''} ${tab.id === 'capture' ? 'is-primary' : ''}`} onClick={() => onView(tab.id)} aria-current={view === tab.id ? 'page' : undefined}>
            <span><Icon name={tab.icon} /></span><small>{tab.label}</small>
          </button>
        ))}
      </nav>
    </div>
  );
}
