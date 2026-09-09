import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { DropResult } from '@hello-pangea/dnd';
import { api } from '@/lib/api';
import type { Task } from '@/types';

interface MovePayload {
  targetListId: string;
  previousPosition?: number;
  nextPosition?: number;
  version: number;
}

export function useKanbanDrag(workspaceId: string, boardId: string) {
  const queryClient = useQueryClient();

  const moveTaskMutation = useMutation({
    mutationFn: async ({ taskId, payload }: { taskId: string; payload: MovePayload }) => {
      const response = await api.patch(`/boards/${boardId}/tasks/${taskId}/move?workspaceId=${workspaceId}`, payload);
      return response.data;
    },
    onMutate: async ({ taskId, payload }) => {
      await queryClient.cancelQueries({ queryKey: ['tasks', boardId] });
      const previousTasks = queryClient.getQueryData<Task[]>(['tasks', boardId]);

      // Optimistic update
      if (previousTasks) {
        queryClient.setQueryData<Task[]>(['tasks', boardId], (old) => {
          if (!old) return old;
          const newPos = payload.previousPosition && payload.nextPosition 
            ? (payload.previousPosition + payload.nextPosition) / 2
            : payload.previousPosition ? payload.previousPosition + 65536 : payload.nextPosition ? payload.nextPosition / 2 : 65536;

          return old.map(t => 
            t.id === taskId 
              ? { ...t, listId: payload.targetListId, position: newPos, version: t.version + 1 }
              : t
          );
        });
      }

      return { previousTasks };
    },
    onError: (_err, _newTodo, context) => {
      if (context?.previousTasks) {
        queryClient.setQueryData(['tasks', boardId], context.previousTasks);
      }
    },
    onSettled: () => {
      // Do not refetch immediately to avoid flashing, WS will sync state if needed,
      // but if we want to be safe:
      // queryClient.invalidateQueries({ queryKey: ['tasks', boardId] });
    },
  });

  const onDragEnd = (result: DropResult, tasks: Task[]) => {
    const { destination, source, draggableId } = result;

    if (!destination) return;
    if (destination.droppableId === source.droppableId && destination.index === source.index) return;

    const targetListId = destination.droppableId;
    
    // Sort tasks in destination list to find prev and next positions
    const destTasks = tasks.filter(t => t.listId === targetListId).sort((a, b) => a.position - b.position);
    
    // Remove the dragged task if it's in the same list to calculate positions correctly
    const filteredDestTasks = destTasks.filter(t => t.id !== draggableId);

    const prevTask = destination.index > 0 ? filteredDestTasks[destination.index - 1] : undefined;
    const nextTask = destination.index < filteredDestTasks.length ? filteredDestTasks[destination.index] : undefined;

    const draggedTask = tasks.find(t => t.id === draggableId);
    if (!draggedTask) return;

    moveTaskMutation.mutate({
      taskId: draggableId,
      payload: {
        targetListId,
        previousPosition: prevTask?.position,
        nextPosition: nextTask?.position,
        version: draggedTask.version,
      }
    });
  };

  return { onDragEnd };
}
