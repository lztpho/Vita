// SPDX-License-Identifier: Apache-2.0
import { useEffect, useRef, useState } from 'react';
import type { ChatSession } from '../types';
import { VitaNative, errorText } from '../native';
import { Icon } from '../components/Icons';
import { SafeMarkdown } from '../components/SafeMarkdown';

export function ChatPage({ newSessionRequest = 0 }: { newSessionRequest?: number }) {
  const [session, setSession] = useState<ChatSession>();
  const [stream, setStream] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const runRef = useRef('');
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => { void VitaNative.getChatSession().then((result) => setSession(result.session)).catch((error) => setNotice(errorText(error))); }, []);
  useEffect(() => {
    const handles: Array<Promise<{ remove: () => Promise<void> }>> = [
      VitaNative.addListener('chatDelta', (event) => { if (event.runId === runRef.current) setStream((current) => current + event.delta); }),
      VitaNative.addListener('chatDone', (event) => { if (event.runId === runRef.current) { setSession(event.session); setStream(''); setBusy(false); } }),
      VitaNative.addListener('chatError', (event) => { if (event.runId === runRef.current) { setNotice(event.message); setBusy(false); } }),
    ];
    return () => { handles.forEach((handle) => void handle.then((value) => value.remove())); };
  }, []);
  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [session?.messages.length, stream]);

  async function send() {
    const message = inputRef.current?.value.trim().slice(0, 2000) || '';
    if (!session || !message || busy) return;
    if (inputRef.current) inputRef.current.value = '';
    setBusy(true); setNotice(''); setStream('');
    setSession({ ...session, messages: [...session.messages, { id: crypto.randomUUID(), role: 'user', content: message, createdAtMs: Date.now() }] });
    try { runRef.current = (await VitaNative.streamChat({ sessionId: session.id, message })).runId; }
    catch (error) { setNotice(errorText(error)); setBusy(false); }
  }

  async function newSession() { try { setSession((await VitaNative.newChatSession()).session); setStream(''); } catch (error) { setNotice(errorText(error)); } }

  useEffect(() => {
    if (newSessionRequest > 0) void newSession();
  }, [newSessionRequest]);

  return <section className="page page--chat">
    <h1 className="sr-only">咨询</h1>
    <div className="messages">{!session?.messages.length && !stream && <div className="chat-empty"><Icon name="chat" /><strong>可以直接问饮食问题</strong><span>例如：今天蛋白质还差多少？晚餐应该怎么搭配？</span></div>}
      {session?.messages.map((message) => <article className={`message message--${message.role}`} key={message.id}><span className="message-avatar">{message.role === 'assistant' ? 'V' : '我'}</span><div>{message.role === 'assistant' ? <SafeMarkdown>{message.content}</SafeMarkdown> : <p className="plain-message">{message.content}</p>}</div></article>)}
      {stream && <article className="message message--assistant"><span className="message-avatar">V</span><div><SafeMarkdown>{stream}</SafeMarkdown></div></article>}
      {busy && !stream && <div className="typing" aria-label="Vita 正在回复"><i /><i /><i /></div>}<div ref={bottomRef} />
    </div>
    {notice && <div className="notice" role="status">{notice}</div>}
    <form className="composer" onSubmit={(event) => { event.preventDefault(); void send(); }}><div className="composer-row"><textarea ref={inputRef} rows={1} required maxLength={2000} aria-label="输入问题" /><button className="button button--primary" disabled={busy} aria-label="发送"><Icon name="send" /><span>发送</span></button></div></form>
  </section>;
}
