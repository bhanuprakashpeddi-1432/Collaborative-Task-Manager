import type { Task } from '@/types';
import { format } from 'date-fns';
import { Clock } from 'lucide-react';
import { clsx } from 'clsx';

interface TaskCardProps {
  task: Task;
}

const priorityColors = {
  LOW: 'bg-green-100 text-green-800',
  MEDIUM: 'bg-blue-100 text-blue-800',
  HIGH: 'bg-orange-100 text-orange-800',
  URGENT: 'bg-red-100 text-red-800',
};

export function TaskCard({ task }: TaskCardProps) {
  return (
    <div className="bg-white p-3 rounded-md shadow-sm border border-gray-200 hover:shadow-md hover:border-gray-300 transition-all cursor-pointer group">
      <div className="flex justify-between items-start mb-2">
        <span
          className={clsx(
            'text-[10px] font-bold px-2 py-0.5 rounded uppercase tracking-wide',
            priorityColors[task.priority] || priorityColors.MEDIUM
          )}
        >
          {task.priority}
        </span>
      </div>
      <h4 className="text-sm font-medium text-gray-900 leading-snug line-clamp-2">
        {task.title}
      </h4>
      
      <div className="mt-3 flex items-center justify-between text-xs text-gray-500">
        {task.dueDate ? (
          <div className="flex items-center gap-1">
            <Clock size={12} />
            <span>{format(new Date(task.dueDate), 'MMM d')}</span>
          </div>
        ) : (
          <div />
        )}
        
        {task.assignedToId && (
          <div className="w-6 h-6 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-700 font-medium text-[10px] border border-white shadow-sm" title="Assigned User">
            {/* Real app would map ID to initials/avatar */}
            U
          </div>
        )}
      </div>
    </div>
  );
}
