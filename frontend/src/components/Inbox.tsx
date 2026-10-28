import { useState } from 'react';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Send, Loader2, Mail, MailOpen } from 'lucide-react';
import { api } from '@/lib/api';
import { useAuthStore } from '@/store/useAuthStore';
import type { InboxContact } from '@/types';

export function Inbox() {
  const queryClient = useQueryClient();
  const { user } = useAuthStore();
  const userId = user?.id;
  const [recipientId, setRecipientId] = useState('');
  const [subject, setSubject] = useState('');
  const [content, setContent] = useState('');

  const { data: contacts = [], isLoading: isLoadingContacts, isError: contactsError } = useQuery<InboxContact[]>({
    queryKey: ['inbox-contacts', userId],
    queryFn: async () => (await api.get('/inbox/contacts')).data,
    enabled: !!userId,
  });
  const {
    data,
    isLoading: isLoadingMessages,
    isError: messagesError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['inbox', userId],
    initialPageParam: 0,
    enabled: !!userId,
    queryFn: async ({ pageParam = 0 }) => (await api.get('/inbox', {
      params: { page: pageParam, size: 20 },
    })).data,
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
  });
  const messages = data?.pages.flatMap((page) => page.content ?? []) ?? [];

  const sendMutation = useMutation({
    mutationFn: async () => (await api.post('/inbox', { recipientId, subject, content })).data,
    onSuccess: async () => {
      setRecipientId('');
      setSubject('');
      setContent('');
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['inbox', userId] }),
        queryClient.invalidateQueries({ queryKey: ['inbox-contacts', userId] }),
      ]);
    },
  });

  const readMutation = useMutation({
    mutationFn: async (messageId: string) => (await api.patch(`/inbox/${messageId}/read`)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['inbox', userId] }),
  });

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
      <section className="rounded-xl border border-stone-200 bg-white p-6 shadow-sm">
        <div className="mb-5 flex items-center gap-3">
          <Mail className="text-teal-700" />
          <div>
            <h2 className="text-xl font-semibold text-teal-950">Inbox</h2>
            <p className="text-sm text-stone-500">Messages sent and received through WorkCollab.</p>
          </div>
        </div>
        {isLoadingMessages ? <p className="text-sm text-stone-500">Loading messages...</p> : messagesError ? (
          <p className="text-sm text-red-600">Unable to load your messages.</p>
        ) : messages.length === 0 ? (
          <p className="rounded-lg bg-stone-50 p-4 text-sm text-stone-500">No messages yet.</p>
        ) : (
          <div className="space-y-3">
            {messages.map((message) => {
              const isReceivedMessage = message.recipientId === userId;
              const isUnreadForCurrentUser = isReceivedMessage && !message.read;
              return (
                <button
                  key={message.id}
                  type="button"
                  onClick={() => isUnreadForCurrentUser && readMutation.mutate(message.id)}
                  className={`w-full rounded-lg border p-4 text-left transition-colors hover:border-teal-300 ${isUnreadForCurrentUser ? 'border-teal-200 bg-teal-50/50' : 'border-stone-200 bg-white'}`}
                >
                  <div className="flex items-start gap-3">
                    {isUnreadForCurrentUser ? <Mail className="mt-1 text-teal-700" size={18} /> : <MailOpen className="mt-1 text-stone-400" size={18} />}
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap justify-between gap-2">
                        <p className="font-semibold text-stone-800">{message.subject}</p>
                        <time className="text-xs text-stone-400">{new Date(message.createdAt).toLocaleString()}</time>
                      </div>
                      <p className="text-sm text-stone-500">{message.senderName} to {message.recipientName}</p>
                      <p className="mt-2 whitespace-pre-wrap text-sm text-stone-700">{message.content}</p>
                    </div>
                  </div>
                </button>
              );
            })}
            {hasNextPage && (
              <button
                type="button"
                onClick={() => fetchNextPage()}
                disabled={isFetchingNextPage}
                className="w-full rounded-lg border border-stone-200 bg-stone-50 px-3 py-2 text-sm font-medium text-stone-700 hover:bg-stone-100 disabled:opacity-60"
              >
                {isFetchingNextPage ? 'Loading more...' : 'Load more messages'}
              </button>
            )}
          </div>
        )}
      </section>

      <section className="rounded-xl border border-stone-200 bg-white p-6 shadow-sm">
        <h3 className="text-lg font-semibold text-teal-950">New message</h3>
        <p className="mt-1 text-sm text-stone-500">Recipients are limited by your role and workspace assignments.</p>
        <form className="mt-5 space-y-4" onSubmit={(event) => { event.preventDefault(); sendMutation.mutate(); }}>
          <select value={recipientId} onChange={(event) => setRecipientId(event.target.value)} required className="w-full rounded-lg border border-stone-300 px-3 py-2 text-sm outline-none focus:border-teal-800" disabled={isLoadingContacts || contactsError}>
            <option value="">{isLoadingContacts ? 'Loading recipients...' : 'Select recipient'}</option>
            {contacts.map((contact) => <option key={contact.id} value={contact.id}>{contact.fullName} ({contact.systemRole})</option>)}
          </select>
          {contactsError && <p className="text-sm text-red-600">Unable to load permitted recipients.</p>}
          {!contactsError && !isLoadingContacts && contacts.length === 0 && <p className="text-sm text-amber-700">No permitted recipients are available.</p>}
          <input value={subject} onChange={(event) => setSubject(event.target.value)} required maxLength={200} placeholder="Subject" className="w-full rounded-lg border border-stone-300 px-3 py-2 text-sm outline-none focus:border-teal-800" />
          <textarea value={content} onChange={(event) => setContent(event.target.value)} required maxLength={10000} rows={7} placeholder="Write your message..." className="w-full resize-y rounded-lg border border-stone-300 px-3 py-2 text-sm outline-none focus:border-teal-800" />
          {sendMutation.isError && <p className="text-sm text-red-600">Unable to send this message.</p>}
          <button type="submit" disabled={sendMutation.isPending || !recipientId || !subject.trim() || !content.trim()} className="flex w-full items-center justify-center gap-2 rounded-lg bg-teal-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-50">
            {sendMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
            Send message
          </button>
        </form>
      </section>
    </div>
  );
}
