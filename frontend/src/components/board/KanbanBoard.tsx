import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import type { Task, TaskList } from '@/types';
import { DragDropContext, Droppable } from '@hello-pangea/dnd';
import { Column } from './Column';
import { useKanbanDrag } from '@/hooks/useKanbanDrag';
import { Plus, X, Loader2 } from 'lucide-react';

interface KanbanBoardProps {
  workspaceId: string;
  boardId: string;
  lists: TaskList[];
  onTaskClick: (task: Task) => void;
}

export function KanbanBoard({ workspaceId, boardId, lists, onTaskClick }: KanbanBoardProps) {
  const queryClient = useQueryClient();

  const { data: tasks = [] } = useQuery<Task[]>({
    queryKey: ['tasks', boardId],
    queryFn: async () => {
      const res = await api.get(`/boards/${boardId}/tasks?workspaceId=${workspaceId}&size=1000`);
      return res.data.content || res.data;
    },
  });

  const { onDragEnd } = useKanbanDrag(workspaceId, boardId);

  // State for new task creation per list
  const [addingToList, setAddingToList] = useState<string | null>(null);
  const [newTaskTitle, setNewTaskTitle] = useState('');

  const createTaskMutation = useMutation({
    mutationFn: async ({ listId, title }: { listId: string; title: string }) => {
      const res = await api.post(`/boards/${boardId}/tasks?workspaceId=${workspaceId}`, {
        listId,
        title,
        priority: 'MEDIUM',
      });
      return res.data;
    },
    onSuccess: (newTask) => {
      queryClient.setQueryData<Task[]>(['tasks', boardId], (old = []) => [...old, newTask]);
      setAddingToList(null);
      setNewTaskTitle('');
    },
    onError: () => {
      alert('Failed to create task. Please try again.');
    },
  });

  const handleCreateTask = (listId: string) => {
    if (!newTaskTitle.trim()) return;
    createTaskMutation.mutate({ listId, title: newTaskTitle.trim() });
  };

  return (
    <DragDropContext onDragEnd={(result) => onDragEnd(result, tasks)}>
      <div className="flex gap-6 h-full overflow-x-auto p-4 items-start">
        {lists.sort((a, b) => a.position - b.position).map((list) => {
          const listTasks = tasks
            .filter((t) => t.listId === list.id)
            .sort((a, b) => a.position - b.position);

          return (
            <div key={list.id} className="bg-gray-100 rounded-lg w-80 shrink-0 max-h-full flex flex-col shadow-sm">
              <div className="p-3 font-semibold text-gray-700 border-b border-gray-200">
                {list.name}
                <span className="ml-2 text-xs text-gray-500 bg-gray-200 py-0.5 px-2 rounded-full">
                  {listTasks.length}
                </span>
              </div>
              <Droppable droppableId={list.id}>
                {(provided, snapshot) => (
                  <div
                    ref={provided.innerRef}
                    {...provided.droppableProps}
                    className={`flex-1 p-2 overflow-y-auto min-h-[150px] transition-colors ${
                      snapshot.isDraggingOver ? 'bg-blue-50/50' : ''
                    }`}
                  >
                    <Column tasks={listTasks} onTaskClick={onTaskClick} />
                    {provided.placeholder}
                  </div>
                )}
              </Droppable>

              {/* Add Task Section */}
              <div className="p-2 border-t border-gray-200">
                {addingToList === list.id ? (
                  <div className="space-y-2">
                    <textarea
                      value={newTaskTitle}
                      onChange={(e) => setNewTaskTitle(e.target.value)}
                      className="w-full p-2 rounded-md border border-gray-300 text-sm focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none resize-none"
                      placeholder="Enter task title..."
                      rows={2}
                      autoFocus
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault();
                          handleCreateTask(list.id);
                        }
                        if (e.key === 'Escape') {
                          setAddingToList(null);
                          setNewTaskTitle('');
                        }
                      }}
                    />
                    <div className="flex gap-2">
                      <button
                        onClick={() => handleCreateTask(list.id)}
                        disabled={!newTaskTitle.trim() || createTaskMutation.isPending}
                        className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-md shadow-sm transition-colors flex items-center gap-1.5 disabled:opacity-50"
                      >
                        {createTaskMutation.isPending ? <Loader2 size={14} className="animate-spin" /> : null}
                        Add
                      </button>
                      <button
                        onClick={() => { setAddingToList(null); setNewTaskTitle(''); }}
                        className="p-1.5 text-gray-500 hover:text-gray-700 transition-colors"
                      >
                        <X size={18} />
                      </button>
                    </div>
                  </div>
                ) : (
                  <button
                    onClick={() => { setAddingToList(list.id); setNewTaskTitle(''); }}
                    className="w-full flex items-center gap-1.5 text-gray-500 hover:text-gray-700 hover:bg-gray-200 rounded-md px-2 py-1.5 text-sm transition-colors"
                  >
                    <Plus size={16} />
                    Add a card
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </DragDropContext>
  );
}
