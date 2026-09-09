import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/store/useAuthStore';
import type { CollaborationMessage, Task, UserPresencePayload } from '@/types';

interface PresenceState {
  [userId: string]: UserPresencePayload;
}

export function useBoardWebSocket(workspaceId: string, boardId: string) {
  const queryClient = useQueryClient();
  const token = useAuthStore((state) => state.token);
  const currentUser = useAuthStore((state) => state.user);
  const clientRef = useRef<Client | null>(null);
  
  const [activeUsers, setActiveUsers] = useState<PresenceState>({});

  useEffect(() => {
    if (!token || !workspaceId || !boardId) return;

    const socketUrl = `http://localhost:8080/ws?token=${token}`;
    
    const client = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      debug: (str) => console.log('STOMP: ' + str),
      reconnectDelay: 5000,
      onConnect: () => {
        console.log('Connected to STOMP');
        
        // Subscribe to the board topic
        const topic = `/topic/workspace.${workspaceId}.boards.${boardId}`;
        client.subscribe(topic, (message) => {
          const collabMsg: CollaborationMessage = JSON.parse(message.body);
          handleRealTimeEvent(collabMsg);
        });
      },
      onStompError: (frame) => {
        console.error('STOMP error', frame);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, [token, workspaceId, boardId]);

  const handleRealTimeEvent = (msg: CollaborationMessage) => {
    // Ignore events triggered by ourselves to avoid double updates
    // (since optimistic UI already applied the change)
    if (msg.triggeredBy === currentUser?.id && msg.eventType !== 'USER_PRESENCE') {
      return;
    }

    const queryKey = ['tasks', boardId];

    switch (msg.eventType) {
      case 'TASK_CREATED': {
        const newTask = msg.payload as Task;
        queryClient.setQueryData<Task[]>(queryKey, (old = []) => [...old, newTask]);
        break;
      }
      case 'TASK_UPDATED':
      case 'TASK_MOVED': {
        const updatedTask = msg.payload as Task;
        queryClient.setQueryData<Task[]>(queryKey, (old = []) => 
          old.map((t) => (t.id === updatedTask.id ? updatedTask : t))
        );
        break;
      }
      case 'TASK_DELETED': {
        const { taskId } = msg.payload as { taskId: string; listId: string };
        queryClient.setQueryData<Task[]>(queryKey, (old = []) => 
          old.filter((t) => t.id !== taskId)
        );
        break;
      }
      case 'USER_PRESENCE': {
        const presence = msg.payload as UserPresencePayload;
        setActiveUsers((prev) => {
          if (presence.action === 'LEFT') {
            const newState = { ...prev };
            delete newState[presence.userId];
            return newState;
          }
          return { ...prev, [presence.userId]: presence };
        });
        break;
      }
    }
  };

  const sendHeartbeat = (taskId: string, action: 'VIEWING' | 'EDITING' | 'LEFT') => {
    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination: '/app/presence.heartbeat',
        body: JSON.stringify({
          workspaceId,
          boardId,
          taskId,
          action,
        }),
      });
    }
  };

  return { activeUsers, sendHeartbeat };
}
