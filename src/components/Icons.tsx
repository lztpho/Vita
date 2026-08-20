// SPDX-License-Identifier: Apache-2.0
import type { SVGProps } from 'react';

export type IconName = 'today' | 'chat' | 'camera' | 'calendar' | 'settings' | 'image' | 'history' | 'send' | 'close' | 'spark' | 'check';

/* Original in-repository glyph system: open corners, clipped inner strokes. */
const paths: Record<IconName, React.ReactNode> = {
  today: <><path d="M6.2 8.2 12 4.8l5.8 3.4v7.6L12 19.2l-5.8-3.4Z"/><path d="M9.3 12h5.4M12 9.3v5.4"/></>,
  chat: <><path d="M5 6.5h14v9.2H10l-4.8 3 .8-3H5Z"/><path d="M8.5 10h7M8.5 12.8h4.2"/></>,
  camera: <><path d="M5 8.2h3.1l1.4-2h5l1.4 2H19v9.6H5Z"/><path d="M9 13a3 3 0 1 0 6 0 3 3 0 0 0-6 0Z"/><path d="M17.1 10.5h.1"/></>,
  calendar: <><path d="M5.2 6.5h13.6v12.3H5.2Z"/><path d="M8.2 4.8v3.1M15.8 4.8v3.1M5.2 10h13.6"/><path d="m9 14 2 2 4-4"/></>,
  settings: <><path d="m12 4.6 2 1.1 2.3-.2 1 2.1 1.8 1.5-.8 2.2.8 2.2-1.8 1.5-1 2.1-2.3-.2-2 1.1-2-1.1-2.3.2-1-2.1-1.8-1.5.8-2.2-.8-2.2 1.8-1.5 1-2.1 2.3.2Z"/><path d="M9.6 12a2.4 2.4 0 1 0 4.8 0 2.4 2.4 0 0 0-4.8 0Z"/></>,
  image: <><path d="M5 6.2h14v11.6H5Z"/><path d="m7.4 15 3-3 2.1 2.1 1.7-1.7 2.4 2.6M15.7 9.4h.1"/></>,
  history: <><path d="M6.6 8.6A6.7 6.7 0 1 1 5.3 13"/><path d="M4.8 6.4v3.4h3.4M12 8.2v4l2.8 1.7"/></>,
  send: <><path d="m5 6 14 6-14 6 2.2-6Z"/><path d="M7.2 12H14"/></>,
  close: <><path d="m7 7 10 10M17 7 7 17"/><path d="M5 12h.1M18.9 12h.1"/></>,
  spark: <><path d="m12 4 1.5 4.5L18 10l-4.5 1.5L12 16l-1.5-4.5L6 10l4.5-1.5Z"/><path d="m18.5 16 .6 1.9 1.9.6-1.9.6-.6 1.9-.6-1.9-1.9-.6 1.9-.6Z"/></>,
  check: <><path d="m6.2 12.2 3.5 3.5 8.1-8.1"/><path d="M5 7.5V5h2.5M16.5 19H19v-2.5"/></>,
};

export function Icon({ name, ...props }: { name: IconName } & SVGProps<SVGSVGElement>) {
  return <svg viewBox="0 0 24 24" aria-hidden="true" {...props}>{paths[name]}</svg>;
}

export function BrandMark({ className = '' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 64 64" role="img" aria-label="Vita">
      <rect x="3" y="3" width="58" height="58" rx="15" fill="#252A5A" stroke="none" />
      <path d="M15.5 31h33c-1.6 10.8-7.7 17-16.5 17s-14.9-6.2-16.5-17Z" fill="#F8F9FC" stroke="none" />
      <path d="M16.5 31h31" fill="none" stroke="#F59E72" strokeWidth="3.5" strokeLinecap="round" />
      <path d="M24 38.5c4.7 3.8 11.3 3.8 16 0" fill="none" stroke="#252A5A" strokeWidth="2.6" strokeLinecap="round" />
      <path d="m40 14.5 1.8 4.7 4.7 1.8-4.7 1.8-1.8 4.7-1.8-4.7-4.7-1.8 4.7-1.8Z" fill="#F59E72" stroke="none" />
      <circle cx="23" cy="22" r="2.5" fill="#F8F9FC" stroke="none" />
    </svg>
  );
}
