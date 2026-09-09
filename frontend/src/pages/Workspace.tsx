import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

import type { Task, TaskList, Board as BoardType } from '@/types';
import { KanbanBoard } from '@/components/board/KanbanBoard';
import { TaskDetailsModal } from '@/components/board/TaskDetailsModal';
import { PresenceAvatars } from '@/components/board/PresenceAvatars';
import { useBoardWebSocket } from '@/hooks/useBoardWebSocket';
import { LayoutDashboard, LogOut } from 'lucide-react';
import { useAuthStore } from '@/store/useAuthStore';
import { useNavigate } from 'react-router-dom';

export function Workspace() {
  const { logout, user } = useAuthStore();
  const navigate = useNavigate();
  
  // For simplicity, hardcoding a workspaceId and boardId or pulling from a query to start
  // In a real app, this would come from URL params (e.g., /workspace/:wId/board/:bId)
  const workspaceId = 'd290f1ee-6c54-4b01-90e6-d701748f0851'; // Example UUID
  const boardId = 'e3b0c442-989b-464c-8650-123456789012';     // Example UUID

  const [selectedTask, setSelectedTask] = useState<Task | null>(null);

  // Use WebSocket for real-time sync and presence
  const { activeUsers, sendHeartbeat } = useBoardWebSocket(workspaceId, boardId);

  // Fetch Board details and Lists
  const { data: boardInfo, isLoading } = useQuery<{ board: BoardType, lists: TaskList[] }>({
    queryKey: ['board', boardId],
    queryFn: async () => {
      // Assuming endpoint returns board details and its lists
      // const res = await api.get(`/boards/${boardId}?workspaceId=${workspaceId}`);
      // return res.data;
      
      // Mocked for demonstration since we only built the Task APIs in backend plan
      return {
        board: { id: boardId, workspaceId, name: 'Engineering Sprint', position: 1, createdAt: new Date().toISOString() },
        lists: [
          { id: 'list-1', boardId, name: 'To Do', position: 1 },
          { id: 'list-2', boardId, name: 'In Progress', position: 2 },
          { id: 'list-3', boardId, name: 'Done', position: 3 },
        ]
      };
    }
  });

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  if (isLoading) {
    return <div className="h-screen flex items-center justify-center">Loading board...</div>;
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
