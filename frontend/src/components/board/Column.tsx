import type { Task } from '@/types';
import { Draggable } from '@hello-pangea/dnd';
import { TaskCard } from './TaskCard';

interface ColumnProps {
  tasks: Task[];
  onTaskClick: (task: Task) => void;
}

export function Column({ tasks, onTaskClick }: ColumnProps) {
  return (
    <div className="space-y-2">
      {tasks.map((task, index) => (
        <Draggable key={task.id} draggableId={task.id} index={index}>
          {(provided, snapshot) => (
            <div
              ref={provided.innerRef}
              {...provided.draggableProps}
              {...provided.dragHandleProps}
              style={{
                ...provided.draggableProps.style,
              }}
              onClick={() => onTaskClick(task)}
              className={snapshot.isDragging ? 'z-50 shadow-xl ring-2 ring-blue-500 rounded-md' : ''}
            >
              <TaskCard task={task} />
            </div>
          )}
        </Draggable>
      ))}
    </div>
  );
}
