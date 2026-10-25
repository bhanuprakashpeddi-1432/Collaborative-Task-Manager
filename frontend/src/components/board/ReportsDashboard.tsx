import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { BarChart2, TrendingUp, CheckCircle, Clock, AlertTriangle } from 'lucide-react';
import type { Task } from '@/types';

interface ReportsDashboardProps {
  workspaceId: string;
  projectId: string;
}

export function ReportsDashboard({ workspaceId, projectId }: ReportsDashboardProps) {
  const { data: tasks = [], isLoading, isError } = useQuery<Task[]>({
    queryKey: ['report-tasks', workspaceId, projectId],
    queryFn: async () => {
      const res = await api.get(`/projects/${projectId}/tasks?workspaceId=${workspaceId}&size=1000`);
      return res.data.content || res.data;
    },
    enabled: !!workspaceId && !!projectId,
  });

  if (isLoading) {
    return (
      <div className="h-full flex items-center justify-center">
        <p className="text-gray-500 animate-pulse">Loading analytics...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="h-full flex items-center justify-center p-6">
        <p className="text-red-600">Unable to load project reports. Please try again.</p>
      </div>
    );
  }

  // Analytics Calculations
  const totalTasks = tasks.length;
  const completedTasks = tasks.filter(t => t.status === 'DONE').length;
  const inProgressTasks = tasks.filter(t => ['IN_PROGRESS', 'IN_REVIEW'].includes(t.status)).length;
  const blockedTasks = tasks.filter(t => t.status === 'BLOCKED').length;
  const todoTasks = tasks.filter(t => ['BACKLOG', 'TODO'].includes(t.status)).length;
  const urgentTasks = tasks.filter(t => t.priority === 'URGENT').length;
  const now = Date.now();
  const overdueTasks = tasks.filter(t =>
    t.dueDate && new Date(t.dueDate).getTime() < now && t.status !== 'DONE'
  ).length;
  const totalEstimatedHours = tasks.reduce((sum, t) => sum + (t.estimatedHours || 0), 0);
  const totalActualHours = tasks.reduce((sum, t) => sum + (t.actualHours || 0), 0);
  const averageProgress = totalTasks > 0
    ? Math.round(tasks.reduce((sum, task) => sum + (task.progress || 0), 0) / totalTasks)
    : 0;
  const completionRate = totalTasks > 0 ? Math.round((completedTasks / totalTasks) * 100) : 0;
  const timeUsageRate = totalEstimatedHours > 0
    ? Math.round((totalActualHours / totalEstimatedHours) * 100)
    : 0;

  return (
    <div className="p-8 h-full overflow-y-auto bg-slate-50">
      <div className="max-w-6xl mx-auto space-y-8">
        
        {/* Header */}
        <div>
          <h2 className="text-3xl font-bold text-slate-800 flex items-center gap-3">
            <BarChart2 className="text-indigo-600" size={32} />
            Project Analytics
          </h2>
          <p className="text-slate-500 mt-2">Overview of project health, task completion, and time tracking.</p>
        </div>

        {/* Top KPI Cards */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          
          <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-slate-500 uppercase">Completion Rate</h3>
              <TrendingUp className="text-emerald-500" size={20} />
            </div>
            <div className="text-4xl font-bold text-slate-800 mb-2">{completionRate}%</div>
            <div className="w-full bg-slate-100 rounded-full h-2">
              <div className="bg-emerald-500 h-2 rounded-full" style={{ width: `${completionRate}%` }}></div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-slate-500 uppercase">Completed Tasks</h3>
              <CheckCircle className="text-blue-500" size={20} />
            </div>
            <div className="text-4xl font-bold text-slate-800">{completedTasks}</div>
            <p className="text-sm text-slate-400 mt-2">out of {totalTasks} total tasks</p>
          </div>

          <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-slate-500 uppercase">In Progress</h3>
              <Clock className="text-amber-500" size={20} />
            </div>
            <div className="text-4xl font-bold text-slate-800">{inProgressTasks}</div>
            <p className="text-sm text-slate-400 mt-2">currently active tasks</p>
          </div>

          <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-slate-500 uppercase">Urgent Tasks</h3>
              <AlertTriangle className="text-red-500" size={20} />
            </div>
            <div className="text-4xl font-bold text-slate-800">{urgentTasks}</div>
            <p className="text-sm text-slate-400 mt-2">requires immediate attention</p>
          </div>

        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          {[
            ['Average Progress', `${averageProgress}%`],
            ['To Do', todoTasks],
            ['Blocked', blockedTasks],
            ['Overdue', overdueTasks],
          ].map(([label, value]) => (
            <div key={label} className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200">
              <p className="text-xs font-semibold uppercase text-slate-500">{label}</p>
              <p className="mt-2 text-3xl font-bold text-slate-800">{value}</p>
            </div>
          ))}
        </div>

        {/* Detailed Metrics */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          
          {/* Time Tracking Box */}
          <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
            <h3 className="text-lg font-bold text-slate-800 mb-6">Time Tracking Analysis</h3>
            <div className="space-y-6">
              
              <div>
                <div className="flex justify-between text-sm mb-2">
                  <span className="font-medium text-slate-600">Total Estimated Hours</span>
                  <span className="font-bold text-slate-800">{totalEstimatedHours}h</span>
                </div>
                <div className="w-full bg-slate-100 rounded-full h-3">
                  <div className="bg-indigo-400 h-3 rounded-full" style={{ width: `${totalEstimatedHours > 0 ? Math.min(100, (totalEstimatedHours / Math.max(totalEstimatedHours, totalActualHours, 1)) * 100) : 0}%` }}></div>
                </div>
              </div>

              <div>
                <div className="flex justify-between text-sm mb-2">
                  <span className="font-medium text-slate-600">Total Actual Hours</span>
                  <span className="font-bold text-slate-800">{totalActualHours}h</span>
                </div>
                <div className="w-full bg-slate-100 rounded-full h-3">
                  <div className={`h-3 rounded-full ${totalActualHours > totalEstimatedHours ? 'bg-red-500' : 'bg-emerald-500'}`} 
                       style={{ width: `${Math.min(100, timeUsageRate)}%` }}></div>
                </div>
              </div>
              
              <div className="p-4 bg-slate-50 rounded-xl border border-slate-100">
                <p className="text-sm text-slate-600 text-center">
                  {totalEstimatedHours === 0
                    ? <span className="text-slate-600 font-medium">No estimated time has been recorded.</span>
                    : totalActualHours > totalEstimatedHours
                    ? <span className="text-red-600 font-medium">Project is currently over time budget.</span>
                    : <span className="text-emerald-600 font-medium">Project is tracking well within time budget.</span>}
                </p>
              </div>

            </div>
          </div>

          {/* Task Priority Distribution Box */}
          <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-200">
            <h3 className="text-lg font-bold text-slate-800 mb-6">Task Priority Distribution</h3>
            <div className="space-y-4">
              
              {['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map(priority => {
                const count = tasks.filter(t => t.priority === priority).length;
                const percentage = totalTasks > 0 ? (count / totalTasks) * 100 : 0;
                
                const colorClass = 
                  priority === 'URGENT' ? 'bg-red-500' :
                  priority === 'HIGH' ? 'bg-orange-500' :
                  priority === 'MEDIUM' ? 'bg-blue-500' : 'bg-slate-400';

                return (
                  <div key={priority}>
                    <div className="flex justify-between text-sm mb-2">
                      <span className="font-medium text-slate-600 uppercase text-xs">{priority}</span>
                      <span className="font-bold text-slate-800">{count} ({Math.round(percentage)}%)</span>
                    </div>
                    <div className="w-full bg-slate-100 rounded-full h-2">
                      <div className={`${colorClass} h-2 rounded-full`} style={{ width: `${percentage}%` }}></div>
                    </div>
                  </div>
                );
              })}

            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
