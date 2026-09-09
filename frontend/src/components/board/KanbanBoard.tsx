import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import type { Task, TaskList } from '@/types';
import { DragDropContext, Droppable } from '@hello-pangea/dnd';
import { Column } from './Column';
import { useKanbanDrag } from '@/hooks/useKanbanDrag';

interface KanbanBoardProps {
  workspaceId: string;
  boardId: string;
  lists: TaskList[];
  onTaskClick: (task: Task) => void;
}

export function KanbanBoard({ workspaceId, boardId, lists, onTaskClick }: KanbanBoardProps) {
  const { data: tasks = [] } = useQuery<Task[]>({
    queryKey: ['tasks', boardId],
    queryFn: async () => {
      // Example endpoint, replace with your actual API structure for paginated/filtered tasks
      const res = await api.get(`/boards/${boardId}/tasks?workspaceId=${workspaceId}&size=1000`);
      return res.data.content || res.data;
    },
  });

  const { onDragEnd } = useKanbanDrag(workspaceId, boardId);

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
            </div>
          );
        })}
      </div>
    </DragDropContext>
  );
}
