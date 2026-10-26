import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import ReactMarkdown from 'react-markdown';
import { X, AlertTriangle, Save, Loader2, Trash2, Clock, CheckCircle2, MessageSquare, Send, History } from 'lucide-react';
import { api } from '@/lib/api';
import type { Task, TaskActivity, Comment } from '@/types';
import { useAuthStore } from '@/store/useAuthStore';


interface TaskDetailsModalProps {
  workspaceId: string;
  boardId: string; // projectId
  task: Task;
  onClose: () => void;
  sendHeartbeat: (taskId: string, action: 'VIEWING' | 'EDITING' | 'LEFT') => void;
}

export function TaskDetailsModal({ workspaceId, boardId: projectId, task, onClose, sendHeartbeat }: TaskDetailsModalProps) {
  const queryClient = useQueryClient();
  const { user } = useAuthStore();
  const [isEditing, setIsEditing] = useState(false);
  const [conflictError, setConflictError] = useState<string | null>(null);

  // Form State
  const [title, setTitle] = useState(task.title);
  const [description, setDescription] = useState(task.description || '');
  const [status, setStatus] = useState(task.status || 'TODO');
  const [priority, setPriority] = useState(task.priority);
  const [estimatedHours, setEstimatedHours] = useState<number | ''>(task.estimatedHours || '');
  const [actualHours, setActualHours] = useState<number | ''>(task.actualHours || '');
  const [progress, setProgress] = useState(task.progress || 0);
  const [assignedToId, setAssignedToId] = useState<string | null>(task.assignedToId);
  const [dueDate, setDueDate] = useState<string>(task.dueDate ? task.dueDate.split('T')[0] : '');

  // Members for assignment
  const { data: members = [] } = useQuery<{id: string, fullName: string, email: string}[]>({
    queryKey: ['workspace-members', workspaceId],
    queryFn: async () => {
      const res = await api.get(`/workspaces/${workspaceId}/members`);
      return res.data;
    }
  });

  // Comments State
  const [newComment, setNewComment] = useState('');

  const { data: comments = [], isLoading: isLoadingComments } = useQuery<Comment[]>({
    queryKey: ['comments', task.id],
    queryFn: async () => {
      const res = await api.get(`/tasks/${task.id}/comments`);
      return res.data;
    }
  });

  const { data: activities = [], isLoading: isLoadingActivities } = useQuery<TaskActivity[]>({
    queryKey: ['task-activity', task.id],
    queryFn: async () => (await api.get(`/tasks/${task.id}/activity`)).data,
  });

  useEffect(() => {
    sendHeartbeat(task.id, isEditing ? 'EDITING' : 'VIEWING');
    return () => {
      sendHeartbeat(task.id, 'LEFT');
    };
  }, [task.id, isEditing, sendHeartbeat]);

  const updateMutation = useMutation({
    mutationFn: async () => {
      const response = await api.put(`/projects/${projectId}/tasks/${task.id}?workspaceId=${workspaceId}`, {
        title,
        description,
        status,
        priority,
        estimatedHours: estimatedHours === '' ? null : estimatedHours,
        actualHours: actualHours === '' ? null : actualHours,
        progress,
        assignedToId,
        dueDate: dueDate ? new Date(dueDate).toISOString() : null,
        version: task.version,
      });
      return response.data;
    },
    onSuccess: (updatedTask) => {
      setIsEditing(false);
      setConflictError(null);
      queryClient.invalidateQueries({ queryKey: ['task-activity', task.id] });
      queryClient.setQueryData<Task[]>(['tasks', projectId], (old) => 
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
      await api.delete(`/projects/${projectId}/tasks/${task.id}?workspaceId=${workspaceId}`);
    },
    onSuccess: () => {
      queryClient.setQueryData<Task[]>(['tasks', projectId], (old) =>
        old?.filter(t => t.id !== task.id)
      );
      onClose();
    },
    onError: () => {
      alert('Failed to delete task.');
    },
  });

  const addCommentMutation = useMutation({
    mutationFn: async () => {
      const res = await api.post(`/tasks/${task.id}/comments`, { content: newComment });
      return res.data;
    },
    onSuccess: (comment) => {
      setNewComment('');
      queryClient.setQueryData<Comment[]>(['comments', task.id], (old = []) => [...old, comment]);
    }
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
    await queryClient.invalidateQueries({ queryKey: ['tasks', projectId] });
    setConflictError(null);
    setIsEditing(false);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4 md:p-8">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl h-full max-h-[85vh] flex overflow-hidden ring-1 ring-slate-200">
        
        {/* Main Left Content */}
        <div className="flex-1 flex flex-col min-w-0 border-r border-slate-200 bg-white">
          <div className="px-6 py-5 flex justify-between items-start border-b border-slate-100">
            {isEditing ? (
              <input
                value={title}
                onChange={e => setTitle(e.target.value)}
                className="w-full text-2xl font-bold border-b-2 border-indigo-200 focus:border-indigo-600 outline-none pb-1 bg-transparent placeholder-slate-300 text-slate-800"
                placeholder="Task title..."
              />
            ) : (
              <h2 className="text-2xl font-bold text-slate-800 leading-tight pr-4 hover:text-indigo-600 cursor-text transition-colors" onDoubleClick={() => setIsEditing(true)}>
                {task.title}
              </h2>
            )}
          </div>

          {conflictError && (
            <div className="bg-orange-50 border-l-4 border-orange-500 p-4 mx-6 my-4 rounded-r-md flex gap-3">
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

          <div className="flex-1 overflow-y-auto p-6 space-y-8">
            {/* Description */}
            <div>
              <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-3">Description</h3>
              {isEditing ? (
                <textarea
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                  className="w-full min-h-[150px] p-4 rounded-xl border border-slate-300 focus:ring-4 focus:ring-indigo-500/10 focus:border-indigo-500 outline-none resize-y text-slate-700 shadow-sm"
                  placeholder="Add a detailed description (Markdown supported)..."
                />
              ) : (
                <div 
                  className="prose prose-slate max-w-none text-slate-700 cursor-text min-h-[100px] p-4 rounded-xl bg-slate-50 border border-slate-100 hover:border-slate-300 transition-colors"
                  onClick={() => setIsEditing(true)}
                >
                  {task.description ? (
                    <ReactMarkdown>{task.description}</ReactMarkdown>
                  ) : (
                    <span className="italic text-slate-400">Click to add a description...</span>
                  )}
                </div>
              )}
            </div>

            {/* Comments Feed */}
            <div>
               <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-4 flex items-center gap-2">
                 <MessageSquare size={16} /> Comments
               </h3>
               
               <div className="space-y-4 mb-6">
                 {isLoadingComments ? (
                   <p className="text-slate-400 text-sm">Loading comments...</p>
                 ) : comments.length === 0 ? (
                   <p className="text-slate-400 text-sm italic">No comments yet.</p>
                 ) : (
                   comments.map(c => (
                     <div key={c.id} className="flex gap-3">
                        <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-blue-500 to-indigo-500 flex items-center justify-center text-white font-bold shadow-sm shrink-0 text-sm">
                          {c.userName.charAt(0)}
                        </div>

                        <div className="flex-1 bg-slate-50 border border-slate-100 rounded-xl p-3">
                          <div className="flex justify-between items-baseline mb-1">
                            <span className="font-semibold text-sm text-slate-800">{c.userName}</span>
                            <span className="text-xs text-slate-400">{new Date(c.createdAt).toLocaleTimeString()}</span>
                          </div>
                          <p className="text-sm text-slate-700">{c.content}</p>
                        </div>
                     </div>
                   ))
                 )}
               </div>

               <div className="mt-8">
                 <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-4 flex items-center gap-2">
                   <History size={16} /> Audit history
                 </h3>
                 {isLoadingActivities ? (
                   <p className="text-slate-400 text-sm">Loading history...</p>
                 ) : activities.length === 0 ? (
                   <p className="text-slate-400 text-sm italic">No changes recorded yet.</p>
                 ) : (
                   <div className="space-y-3">
                     {activities.map(activity => {
                       let detail: { field?: string; value?: string } = {};
                       try { detail = JSON.parse(activity.details); } catch { /* Older activity entries may contain plain JSON. */ }
                       const labels: Record<string, string> = {
                         DESCRIPTION_CHANGED: 'changed the description',
                         STATUS_CHANGED: `changed status to ${detail.value ?? 'a new status'}`,
                         PRIORITY_CHANGED: `changed priority to ${detail.value ?? 'a new priority'}`,
                         TASK_CREATED: 'created the task',
                         TASK_MOVED: 'moved the task',
                         TASK_UPDATED: 'updated the task',
                       };
                       return (
                         <div key={activity.id} className="flex gap-3 text-sm">
                           <div className="w-7 h-7 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center font-semibold shrink-0">
                             {activity.userName.charAt(0)}
                           </div>
                           <div className="min-w-0">
                             <p className="text-slate-700"><span className="font-semibold">{activity.userName}</span> {labels[activity.actionType] ?? 'updated the task'}.</p>
                             <p className="text-xs text-slate-400 mt-0.5">{new Date(activity.createdAt).toLocaleString()}</p>
                           </div>
                         </div>
                       );
                     })}
                   </div>
                 )}
               </div>

               {/* Add Comment Input */}
               <div className="flex gap-3">
                 <div className="w-8 h-8 rounded-full bg-slate-200 flex items-center justify-center text-slate-500 font-bold shrink-0 text-sm">
                   {user?.fullName?.charAt(0) || 'U'}
                 </div>
                 <div className="flex-1 relative">
                   <textarea
                     value={newComment}
                     onChange={e => setNewComment(e.target.value)}
                     className="w-full pl-3 pr-10 py-2 rounded-xl border border-slate-300 focus:ring-4 focus:ring-indigo-500/10 focus:border-indigo-500 outline-none resize-none text-sm shadow-sm"
                     placeholder="Write a comment... use @mention to notify someone."
                     rows={2}
                     onKeyDown={(e) => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault();
                          if (newComment.trim()) addCommentMutation.mutate();
                        }
                      }}
                   />
                   <button 
                    onClick={() => { if(newComment.trim()) addCommentMutation.mutate() }}
                    disabled={!newComment.trim() || addCommentMutation.isPending}
                    className="absolute right-2 top-2 p-1.5 text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors disabled:opacity-50"
                   >
                     {addCommentMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
                   </button>
                 </div>
               </div>
            </div>
          </div>
        </div>

        {/* Sidebar Right Properties */}
        <div className="w-80 bg-slate-50 flex flex-col shrink-0">
          <div className="p-4 border-b border-slate-200 flex justify-end">
            <button onClick={onClose} className="p-2 bg-white hover:bg-slate-200 rounded-full text-slate-500 transition-colors shadow-sm border border-slate-200">
              <X size={18} />
            </button>
          </div>
          
          <div className="p-6 flex-1 overflow-y-auto space-y-6">
            
            {/* Assignment & Dates */}
            <div className="space-y-4">
              <div>
                <label className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1 block">Assignee</label>
                {isEditing ? (
                  <select
                    value={assignedToId || ''}
                    onChange={e => setAssignedToId(e.target.value || null)}
                    className="w-full p-2 text-sm rounded-lg border border-slate-300 focus:border-indigo-500 outline-none"
                  >
                    <option value="">Unassigned</option>
                    {members.map(m => (
                      <option key={m.id} value={m.id}>{m.fullName}</option>
                    ))}
                  </select>
                ) : (
                  <div className="flex items-center gap-2 text-sm font-medium text-slate-700 bg-white border border-slate-200 px-3 py-1.5 rounded-lg">
                    {assignedToId ? members.find(m => m.id === assignedToId)?.fullName || 'Loading...' : 'Unassigned'}
                  </div>
                )}
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1 block">Due Date</label>
                {isEditing ? (
                  <input
                    type="date"
                    value={dueDate}
                    onChange={e => setDueDate(e.target.value)}
                    className="w-full p-2 text-sm rounded-lg border border-slate-300 focus:border-indigo-500 outline-none"
                  />
                ) : (
                  <div className="text-sm font-medium text-slate-800">
                    {dueDate ? new Date(dueDate).toLocaleDateString() : 'No due date'}
                  </div>
                )}
              </div>
            </div>

            <hr className="border-slate-200" />

            {/* Status & Priority */}
            <div className="space-y-4">
              <div>
                <label className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1 block">Status</label>
                {isEditing ? (
                  <select 
                    value={status} 
                    onChange={e => setStatus(e.target.value)}
                    className="w-full p-2 text-sm rounded-lg border border-slate-300 focus:border-indigo-500 outline-none"
                  >
                    <option value="BACKLOG">Backlog</option>
                    <option value="TODO">To Do</option>
                    <option value="IN_PROGRESS">In Progress</option>
                    <option value="IN_REVIEW">In Review</option>
                    <option value="DONE">Done</option>
                  </select>
                ) : (
                  <div className="flex items-center gap-2 text-sm font-medium text-slate-700 bg-white border border-slate-200 px-3 py-1.5 rounded-lg">
                    <CheckCircle2 size={16} className="text-emerald-500" />
                    {status.replace('_', ' ')}
                  </div>
                )}
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1 block">Priority</label>
                {isEditing ? (
                  <select 
                    value={priority} 
                    onChange={e => setPriority(e.target.value as any)}
                    className="w-full p-2 text-sm rounded-lg border border-slate-300 focus:border-indigo-500 outline-none"
                  >
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                    <option value="URGENT">Urgent</option>
                  </select>
                ) : (
                  <div className={`inline-flex px-3 py-1 rounded-lg text-xs font-bold uppercase tracking-wider border ${
                    priority === 'URGENT' ? 'bg-red-50 text-red-700 border-red-200' :
                    priority === 'HIGH' ? 'bg-orange-50 text-orange-700 border-orange-200' :
                    priority === 'MEDIUM' ? 'bg-blue-50 text-blue-700 border-blue-200' :
                    'bg-slate-100 text-slate-700 border-slate-200'
                  }`}>
                    {priority}
                  </div>
                )}
              </div>
            </div>

            <hr className="border-slate-200" />

            {/* Time Tracking */}
            <div className="space-y-4">
              <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                <Clock size={14} /> Time Tracking
              </h3>
              
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-slate-500 block mb-1">Estimated (h)</label>
                  {isEditing ? (
                    <input 
                      type="number" 
                      value={estimatedHours} 
                      onChange={e => setEstimatedHours(e.target.value === '' ? '' : Number(e.target.value))}
                      className="w-full p-2 text-sm rounded-lg border border-slate-300 focus:border-indigo-500 outline-none" 
                    />
                  ) : (
                    <div className="text-sm font-medium text-slate-800">{estimatedHours || '--'}</div>
                  )}
                </div>
                <div>
                  <label className="text-xs text-slate-500 block mb-1">Actual (h)</label>
                  {isEditing ? (
                    <input 
                      type="number" 
                      value={actualHours} 
                      onChange={e => setActualHours(e.target.value === '' ? '' : Number(e.target.value))}
                      className="w-full p-2 text-sm rounded-lg border border-slate-300 focus:border-indigo-500 outline-none" 
                    />
                  ) : (
                    <div className="text-sm font-medium text-slate-800">{actualHours || '--'}</div>
                  )}
                </div>
              </div>

              <div>
                <label className="text-xs text-slate-500 block mb-1">Progress (%)</label>
                {isEditing ? (
                  <input 
                    type="range" 
                    min="0" max="100" step="5"
                    value={progress} 
                    onChange={e => setProgress(Number(e.target.value))}
                    className="w-full accent-indigo-600" 
                  />
                ) : (
                  <div className="w-full bg-slate-200 rounded-full h-2.5 mt-2">
                    <div className="bg-indigo-600 h-2.5 rounded-full" style={{ width: `${progress}%` }}></div>
                  </div>
                )}
                <div className="text-right text-xs text-slate-500 mt-1">{progress}%</div>
              </div>
            </div>

          </div>
          
          <div className="p-4 bg-white border-t border-slate-200">
            {isEditing ? (
              <div className="flex flex-col gap-2">
                <button
                  onClick={handleSave}
                  disabled={updateMutation.isPending}
                  className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium rounded-xl shadow-md transition-colors flex items-center justify-center gap-2 disabled:opacity-50"
                >
                  {updateMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
                  Save Changes
                </button>
                <button 
                  onClick={() => setIsEditing(false)}
                  className="w-full py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-xl transition-colors"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <div className="flex justify-between items-center">
                <button
                  onClick={handleDelete}
                  disabled={deleteMutation.isPending}
                  className="text-slate-400 hover:bg-red-50 hover:text-red-600 transition-colors p-2 rounded-lg border border-transparent hover:border-red-100 flex items-center gap-1 text-xs font-medium" 
                >
                  {deleteMutation.isPending ? <Loader2 size={14} className="animate-spin" /> : <Trash2 size={14} />}
                  Delete
                </button>
                <button 
                  onClick={() => setIsEditing(true)}
                  className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white text-sm font-medium rounded-xl shadow-md transition-colors"
                >
                  Edit Task
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
