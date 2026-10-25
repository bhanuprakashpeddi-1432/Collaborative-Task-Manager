import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import type { Task, TaskList } from '@/types';
import { DragDropContext, Droppable } from '@hello-pangea/dnd';
import { Column } from './Column';
import { useKanbanDrag } from '@/hooks/useKanbanDrag';
import { Plus, X, Loader2 } from 'lucide-react';

import { Filter } from 'lucide-react';

interface KanbanBoardProps {
  workspaceId: string;
  boardId: string;
  lists: TaskList[];
  onTaskClick: (task: Task) => void;
}

export function KanbanBoard({ workspaceId, boardId, lists, onTaskClick }: KanbanBoardProps) {
  const queryClient = useQueryClient();

  const [filterPriority, setFilterPriority] = useState<string>('');
  const [searchString, setSearchString] = useState<string>('');
  
  const { data: tasks = [] } = useQuery<Task[]>({
    queryKey: ['tasks', boardId, filterPriority, searchString],
    queryFn: async () => {
      let url = `/projects/${boardId}/tasks?workspaceId=${workspaceId}&size=1000`;
      if (filterPriority) url += `&priority=${filterPriority}`;
      if (searchString) url += `&searchString=${encodeURIComponent(searchString)}`;
      const res = await api.get(url);
      return res.data.content || res.data;
    },
  });

  const { onDragEnd } = useKanbanDrag(workspaceId, boardId);

  // State for new task creation per list
  const [addingToList, setAddingToList] = useState<string | null>(null);
  const [newTaskTitle, setNewTaskTitle] = useState('');
  const [newTaskPriority, setNewTaskPriority] = useState<'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'>('MEDIUM');

  const createTaskMutation = useMutation({
    mutationFn: async ({ listId, title, priority }: { listId: string; title: string; priority: typeof newTaskPriority }) => {
      const res = await api.post(`/projects/${boardId}/tasks?workspaceId=${workspaceId}`, {
        listId,
        projectId: boardId,
        title,
        priority,
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks', boardId] });
      setAddingToList(null);
      setNewTaskTitle('');
      setNewTaskPriority('MEDIUM');
    },
    onError: () => {
      alert('Failed to create task. Please try again.');
    },
  });

  const handleCreateTask = (listId: string) => {
    if (!newTaskTitle.trim()) return;
    createTaskMutation.mutate({ listId, title: newTaskTitle.trim(), priority: newTaskPriority });
  };

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Filter Bar */}
      <div className="px-6 py-3 bg-white border-b border-gray-200 flex items-center gap-4 shadow-sm shrink-0">
        <div className="flex items-center gap-2 text-gray-500 font-medium text-sm">
          <Filter size={16} /> Filters:
        </div>
        
        <input 
          type="text"
          placeholder="Search tasks..."
          value={searchString}
          onChange={e => setSearchString(e.target.value)}
          className="px-3 py-1.5 border border-gray-300 rounded-lg text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-shadow w-64 bg-gray-50"
        />
        
        <select
          value={filterPriority}
          onChange={e => setFilterPriority(e.target.value)}
          className="px-3 py-1.5 border border-gray-300 rounded-lg text-sm outline-none focus:border-indigo-500 bg-gray-50"
        >
          <option value="">All Priorities</option>
          <option value="LOW">Low</option>
          <option value="MEDIUM">Medium</option>
          <option value="HIGH">High</option>
          <option value="URGENT">Urgent</option>
        </select>
      </div>

      <DragDropContext onDragEnd={(result) => onDragEnd(result, tasks)}>
        <div className="flex gap-6 flex-1 overflow-x-auto p-4 items-start">
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
                    <select
                      value={newTaskPriority}
                      onChange={(e) => setNewTaskPriority(e.target.value as typeof newTaskPriority)}
                      className="w-full rounded-md border border-gray-300 p-2 text-sm outline-none focus:border-blue-500"
                      aria-label="Task priority"
                    >
                      <option value="LOW">Low priority</option>
                      <option value="MEDIUM">Medium priority</option>
                      <option value="HIGH">High priority</option>
                      <option value="URGENT">Urgent priority</option>
                    </select>
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
    </div>
  );
}
