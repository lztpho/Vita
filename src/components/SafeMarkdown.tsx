// SPDX-License-Identifier: Apache-2.0
import type { ComponentProps } from 'react';
import ReactMarkdown from 'react-markdown';

const allowedElements = [
  'p', 'br', 'strong', 'em', 'del', 'code', 'pre', 'blockquote',
  'ul', 'ol', 'li', 'h1', 'h2', 'h3', 'h4', 'a',
];

function safeHttpsUrl(value: string): string {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password ? url.toString() : '';
  } catch {
    return '';
  }
}

export function SafeMarkdown({ children }: { children: string }) {
  return <ReactMarkdown
    allowedElements={allowedElements}
    skipHtml
    unwrapDisallowed
    urlTransform={safeHttpsUrl}
    components={{
      a: ({ href, children: linkChildren }: ComponentProps<'a'>) => {
        const safe = href ? safeHttpsUrl(href) : '';
        return safe
          ? <a href={safe} target="_blank" rel="noopener noreferrer">{linkChildren}</a>
          : <span>{linkChildren}</span>;
      },
    }}
  >{children}</ReactMarkdown>;
}
