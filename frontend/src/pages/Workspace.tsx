import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';

import type { Task, TaskList, Board as BoardType } from '@/types';
import { KanbanBoard } from '@/components/board/KanbanBoard';
import { TaskDetailsModal } from '@/components/board/TaskDetailsModal';
import { PresenceAvatars } from '@/components/board/PresenceAvatars';
import { useBoardWebSocket } from '@/hooks/useBoardWebSocket';
import { LayoutDashboard, LogOut, Loader2 } from 'lucide-react';
import { useAuthStore } from '@/store/useAuthStore';
import { useNavigate } from 'react-router-dom';

export function Workspace() {
  const { logout, user } = useAuthStore();
  const navigate = useNavigate();

  const [selectedTask, setSelectedTask] = useState<Task | null>(null);

  // Step 1: Fetch user's workspaces
  const { data: workspaces, isLoading: isLoadingWorkspaces } = useQuery<{ id: string; name: string; slug: string; role: string }[]>({
    queryKey: ['workspaces'],
    queryFn: async () => {
      const res = await api.get('/workspaces');
      return res.data;
    },
  });

  const workspaceId = workspaces?.[0]?.id;

  // Step 2: Fetch boards in the workspace
  const { data: boards, isLoading: isLoadingBoards } = useQuery<{ id: string; workspaceId: string; name: string; position: number; createdAt: string }[]>({
    queryKey: ['boards', workspaceId],
    queryFn: async () => {
      const res = await api.get(`/workspaces/${workspaceId}/boards`);
      return res.data;
    },
    enabled: !!workspaceId,
  });

  const boardId = boards?.[0]?.id;

  // Step 3: Fetch board details and lists
  const { data: boardInfo, isLoading: isLoadingBoard } = useQuery<{ board: BoardType; lists: TaskList[] }>({
    queryKey: ['board', boardId],
    queryFn: async () => {
      const res = await api.get(`/boards/${boardId}`);
      return res.data;
    },
    enabled: !!boardId,
  });

  // Use WebSocket for real-time sync and presence
  const { activeUsers, sendHeartbeat } = useBoardWebSocket(workspaceId ?? '', boardId ?? '');

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const isLoading = isLoadingWorkspaces || isLoadingBoards || isLoadingBoard;

  if (isLoading) {
    return (
      <div className="h-screen flex items-center justify-center bg-gray-50">
        <div className="flex flex-col items-center gap-3">
          <Loader2 size={32} className="animate-spin text-blue-600" />
          <span className="text-gray-500 text-sm">Loading your workspace...</span>
        </div>
      </div>
    );
  }

  if (!workspaceId || !boardId || !boardInfo) {
    return (
      <div className="h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <h2 className="text-xl font-bold text-gray-800 mb-2">No workspace found</h2>
          <p className="text-gray-500">Please contact an administrator or try logging in again.</p>
          <button onClick={handleLogout} className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg">
            Back to Login
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col bg-gray-50 overflow-hidden">
      {/* Top Navbar */}
      <header className="h-14 bg-white border-b border-gray-200 px-4 flex items-center justify-between shrink-0 shadow-sm z-10">
        <div className="flex items-center gap-3">
          <div className="bg-blue-600 p-1.5 rounded-md text-white">
            <LayoutDashboard size={18} />
          </div>
          <h1 className="font-bold text-gray-800 text-lg">{boardInfo?.board.name}</h1>
        </div>
        
        <div className="flex items-center gap-6">
          <PresenceAvatars activeUsers={activeUsers} />
          
          <div className="h-6 w-px bg-gray-200" />
          
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium text-gray-700">{user?.fullName}</span>
            <button onClick={handleLogout} className="text-gray-500 hover:text-red-600 transition-colors p-1" title="Logout">
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </header>

      {/* Board Content */}
      <main className="flex-1 overflow-hidden">
        {boardInfo && (
          <KanbanBoard 
            workspaceId={workspaceId} 
            boardId={boardId} 
            lists={boardInfo.lists} 
            onTaskClick={setSelectedTask} 
          />
        )}
      </main>

      {/* Modals */}
      {selectedTask && (
        <TaskDetailsModal
          workspaceId={workspaceId}
          boardId={boardId}
          task={selectedTask}
          onClose={() => setSelectedTask(null)}
          sendHeartbeat={sendHeartbeat}
        />
      )}
    </div>
  );
}
