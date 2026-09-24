import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import ReactMarkdown from 'react-markdown';
import { X, AlertTriangle, Save, Loader2, Trash2 } from 'lucide-react';
import { api } from '@/lib/api';
import type { Task } from '@/types';


interface TaskDetailsModalProps {
  workspaceId: string;
  boardId: string;
  task: Task;
  onClose: () => void;
  sendHeartbeat: (taskId: string, action: 'VIEWING' | 'EDITING' | 'LEFT') => void;
}

export function TaskDetailsModal({ workspaceId, boardId, task, onClose, sendHeartbeat }: TaskDetailsModalProps) {
  const queryClient = useQueryClient();
  const [isEditing, setIsEditing] = useState(false);
  const [title, setTitle] = useState(task.title);
  const [description, setDescription] = useState(task.description || '');
  const [conflictError, setConflictError] = useState<string | null>(null);

  useEffect(() => {
    sendHeartbeat(task.id, isEditing ? 'EDITING' : 'VIEWING');
    return () => {
      sendHeartbeat(task.id, 'LEFT');
    };
  }, [task.id, isEditing, sendHeartbeat]);

  const updateMutation = useMutation({
    mutationFn: async () => {
      const response = await api.put(`/boards/${boardId}/tasks/${task.id}?workspaceId=${workspaceId}`, {
        title,
        description,
        priority: task.priority,
        version: task.version,
      });
      return response.data;
    },
    onSuccess: (updatedTask) => {
      setIsEditing(false);
      setConflictError(null);
      queryClient.setQueryData<Task[]>(['tasks', boardId], (old) => 
        old?.map(t => t.id === updatedTask.id ? updatedTask : t)
      );
    },
    onError: (error: any) => {
      if (error.response?.status === 409) {
        setConflictError("This task was updated by someone else while you were editing. Please reload to see the latest changes.");
      } else {
        alert("Failed to update task.");
      }
    }
  });

  const deleteMutation = useMutation({
    mutationFn: async () => {
      await api.delete(`/boards/${boardId}/tasks/${task.id}?workspaceId=${workspaceId}`);
    },
    onSuccess: () => {
      queryClient.setQueryData<Task[]>(['tasks', boardId], (old) =>
        old?.filter(t => t.id !== task.id)
      );
      onClose();
    },
    onError: () => {
      alert('Failed to delete task.');
    },
  });

  const handleSave = () => {
    updateMutation.mutate();
  };

  const handleDelete = () => {
    if (confirm('Are you sure you want to delete this task?')) {
      deleteMutation.mutate();
    }
  };

  const handleReload = async () => {
    // In a real app we might refetch just this task, or invalidate the whole board
    await queryClient.invalidateQueries({ queryKey: ['tasks', boardId] });
    setConflictError(null);
    setIsEditing(false);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden">
        
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-100 flex justify-between items-center bg-gray-50/50">
          <div className="flex items-center gap-3">
            <span className="text-xs font-semibold px-2 py-1 bg-gray-200 text-gray-700 rounded uppercase tracking-wider">
              {task.priority}
            </span>
            <span className="text-sm text-gray-500 font-mono">#{task.id.slice(0,8)}</span>
          </div>
          <button onClick={onClose} className="p-2 hover:bg-gray-200 rounded-full text-gray-500 transition-colors">
            <X size={20} />
          </button>
        </div>

        {/* Conflict Banner */}
        {conflictError && (
          <div className="bg-orange-50 border-l-4 border-orange-500 p-4 m-6 mb-0 rounded-r-md flex gap-3">
            <AlertTriangle className="text-orange-500 shrink-0 mt-0.5" size={20} />
            <div>
              <h3 className="text-sm font-bold text-orange-800">Version Conflict</h3>
              <p className="text-sm text-orange-700 mt-1">{conflictError}</p>
              <div className="mt-3 flex gap-3">
                <button onClick={handleReload} className="text-sm bg-orange-100 hover:bg-orange-200 text-orange-800 px-3 py-1.5 rounded-md font-medium transition-colors">
                  Reload Latest
                </button>
                <button onClick={() => setConflictError(null)} className="text-sm text-orange-600 hover:text-orange-800 font-medium px-3 py-1.5">
                  Keep Editing
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Body */}
        <div className="p-6 overflow-y-auto flex-1">
          {isEditing ? (
            <div className="space-y-4">
              <input
                value={title}
                onChange={e => setTitle(e.target.value)}
                className="w-full text-xl font-bold border-b border-gray-200 focus:border-blue-500 outline-none pb-2 bg-transparent"
                placeholder="Task title..."
              />
              <textarea
                value={description}
                onChange={e => setDescription(e.target.value)}
                className="w-full min-h-[200px] p-3 rounded-md border border-gray-200 focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none resize-y"
                placeholder="Add a detailed description (Markdown supported)..."
              />
            </div>
          ) : (
            <div className="space-y-6">
              <h2 className="text-2xl font-bold text-gray-900 leading-tight" onDoubleClick={() => setIsEditing(true)}>
                {task.title}
              </h2>
              <div 
                className="prose prose-sm max-w-none text-gray-600 cursor-text min-h-[100px] p-4 rounded-lg bg-gray-50 border border-transparent hover:border-gray-200 transition-colors"
                onClick={() => setIsEditing(true)}
              >
                {task.description ? (
                  <ReactMarkdown>{task.description}</ReactMarkdown>
                ) : (
                  <span className="italic text-gray-400">Click to add a description...</span>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 bg-gray-50 border-t border-gray-200 flex justify-between items-center">
          <div className="flex items-center gap-3">
            <div className="text-xs text-gray-500">
              Version {task.version} • Last updated {new Date(task.updatedAt).toLocaleTimeString()}
            </div>
            <button
              onClick={handleDelete}
              disabled={deleteMutation.isPending}
              className="text-gray-400 hover:text-red-600 transition-colors p-1 rounded" 
              title="Delete task"
            >
              {deleteMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Trash2 size={16} />}
            </button>
          </div>
          
          {isEditing && (
            <div className="flex gap-2">
              <button 
                onClick={() => setIsEditing(false)}
                className="px-4 py-2 text-sm font-medium text-gray-600 hover:text-gray-900 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={updateMutation.isPending}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-md shadow-sm transition-colors flex items-center gap-2 disabled:opacity-50"
              >
                {updateMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
                Save
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
