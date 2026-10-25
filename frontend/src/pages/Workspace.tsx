import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';

import type { Task, TaskList, Board as BoardType } from '@/types';
import { KanbanBoard } from '@/components/board/KanbanBoard';
import { TaskDetailsModal } from '@/components/board/TaskDetailsModal';
import { ReportsDashboard } from '@/components/board/ReportsDashboard';
import { PresenceAvatars } from '@/components/board/PresenceAvatars';
import { useBoardWebSocket } from '@/hooks/useBoardWebSocket';
import { LayoutDashboard, LogOut, Loader2, Settings, Briefcase, Inbox, BarChart2, CheckSquare, Plus } from 'lucide-react';
import { useAuthStore } from '@/store/useAuthStore';
import { useNavigate } from 'react-router-dom';

export function Workspace() {
  const { logout, user } = useAuthStore();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isAdmin = user?.systemRole === 'ADMIN';

  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [activeTab, setActiveTab] = useState('projects');
  const [workspaceName, setWorkspaceName] = useState('');
  const [managerId, setManagerId] = useState('');
  const [projectName, setProjectName] = useState('');
  const [projectDescription, setProjectDescription] = useState('');
  const [isProjectFormOpen, setIsProjectFormOpen] = useState(false);
  const isManager = user?.systemRole === 'MANAGER';
  const [selectedProjectId, setSelectedProjectId] = useState<string>();

  const {
    data: managers = [],
    isLoading: isLoadingManagers,
    isError: isManagersError,
  } = useQuery<{ id: string; fullName: string; email: string }[]>({
    queryKey: ['admin-managers'],
    queryFn: async () => (await api.get('/admin/managers')).data,
    enabled: isAdmin,
  });

  const createWorkspaceMutation = useMutation({
    mutationFn: async () => (await api.post('/workspaces', { name: workspaceName, managerId })).data,
    onSuccess: async (workspace) => {
      setWorkspaceName('');
      setManagerId('');
      await queryClient.invalidateQueries({ queryKey: ['workspaces'] });
      setSelectedWorkspaceId(workspace.id);
    },
  });

  const createProjectMutation = useMutation({
    mutationFn: async () => (await api.post(`/workspaces/${workspaceId}/projects`, {
      name: projectName,
      description: projectDescription,
    })).data,
    onSuccess: async (project) => {
      setProjectName('');
      setProjectDescription('');
      setIsProjectFormOpen(false);
      await queryClient.invalidateQueries({ queryKey: ['projects', workspaceId] });
      setSelectedProjectId(project.id);
    },
  });

  // Step 1: Fetch user's workspaces
  const { data: workspaces, isLoading: isLoadingWorkspaces } = useQuery<{ id: string; name: string; slug: string; role: string }[]>({
    queryKey: ['workspaces'],
    queryFn: async () => {
      const res = await api.get('/workspaces');
      return res.data;
    },
  });

  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string>();
  const workspaceId = selectedWorkspaceId ?? workspaces?.[0]?.id;

  useEffect(() => {
    if (workspaces?.length && !selectedWorkspaceId) {
      setSelectedWorkspaceId(workspaces[0].id);
    }
  }, [workspaces, selectedWorkspaceId]);

  useEffect(() => {
    setSelectedProjectId(undefined);
  }, [workspaceId]);

  // Step 2: Fetch projects in the workspace (formerly boards)
  const { data: projects, isLoading: isLoadingProjects } = useQuery<{ id: string; workspaceId: string; name: string; position: number; createdAt: string }[]>({
    queryKey: ['projects', workspaceId],
    queryFn: async () => {
      // Adjusted API call based on Phase 4 changes (assuming boards -> projects API)
      try {
        const res = await api.get(`/workspaces/${workspaceId}/projects`);
        return Array.isArray(res.data) ? res.data : (res.data.content || []);
      } catch (e) {
        // Fallback if endpoint structure varies
        const res = await api.get(`/workspaces/${workspaceId}/projects`).catch(() => ({ data: [] }));
        return res.data;
      }
    },
    enabled: !!workspaceId,
  });

  const projectId = selectedProjectId ?? projects?.[0]?.id;

  useEffect(() => {
    if (projects?.length && (!selectedProjectId || !projects.some((project) => project.id === selectedProjectId))) {
      setSelectedProjectId(projects[0].id);
    }
  }, [projects, selectedProjectId]);

  // Step 3: Fetch project details and lists
  const { data: projectInfo, isLoading: isLoadingProject } = useQuery<{ project: BoardType; lists: TaskList[] }>({
    queryKey: ['project', projectId],
    queryFn: async () => {
      // Changed to support new endpoints, fallback to old if not found
      try {
        const res = await api.get(`/projects/${projectId}`);
        return res.data;
      } catch {
        const res = await api.get(`/projects/${projectId}`);
        return res.data;
      }
    },
    enabled: !!projectId,
  });

  // Use WebSocket for real-time sync and presence
  const { activeUsers, sendHeartbeat } = useBoardWebSocket(workspaceId ?? '', projectId ?? '');

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const isLoading = isLoadingWorkspaces || isLoadingProjects || isLoadingProject;

  if (isLoading) {
    return (
      <div className="h-screen flex items-center justify-center bg-gradient-to-br from-indigo-50 to-blue-100">
        <div className="flex flex-col items-center gap-3 bg-white/50 p-8 rounded-2xl backdrop-blur-md shadow-xl border border-white/20">
          <Loader2 size={40} className="animate-spin text-indigo-600" />
          <span className="text-indigo-900 font-medium tracking-wide">Loading workspace...</span>
        </div>
      </div>
    );
  }

  if (!workspaceId) {
    return (
      <div className="h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-slate-200">
        <div className="text-center bg-white p-10 rounded-3xl shadow-2xl border border-gray-100 max-w-md w-full">
          {isAdmin ? (
            <>
              <h2 className="text-2xl font-bold text-slate-800 mb-3">Create a workspace</h2>
              <p className="text-slate-500 mb-6">Create a workspace and assign it to a manager.</p>
              <form
                className="space-y-3 text-left"
                onSubmit={(event) => {
                  event.preventDefault();
                  createWorkspaceMutation.mutate();
                }}
              >
                <input
                  value={workspaceName}
                  onChange={(event) => setWorkspaceName(event.target.value)}
                  placeholder="Workspace name"
                  required
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 outline-none focus:border-indigo-500"
                />
                <select
                  value={managerId}
                  onChange={(event) => setManagerId(event.target.value)}
                  required
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 outline-none focus:border-indigo-500"
                >
                  <option value="">
                    {isLoadingManagers ? 'Loading managers...' : 'Select manager'}
                  </option>
                  {managers.map((manager) => (
                    <option key={manager.id} value={manager.id}>{manager.fullName} ({manager.email})</option>
                  ))}
                </select>
                {isManagersError && (
                  <p className="text-sm text-red-600">Unable to load the manager list. Please refresh and try again.</p>
                )}
                {!isLoadingManagers && !isManagersError && managers.length === 0 && (
                  <p className="text-sm text-amber-600">No manager accounts are available.</p>
                )}
                {createWorkspaceMutation.isError && (
                  <p className="text-sm text-red-600">Unable to create workspace. Check the name and manager.</p>
                )}
                <button
                  type="submit"
                  disabled={createWorkspaceMutation.isPending || !workspaceName.trim() || !managerId || managers.length === 0}
                  className="w-full py-3 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-xl font-medium"
                >
                  {createWorkspaceMutation.isPending ? 'Creating...' : 'Create workspace'}
                </button>
              </form>
            </>
          ) : (
            <>
              <h2 className="text-2xl font-bold text-slate-800 mb-3">Welcome to WorkCollab</h2>
              <p className="text-slate-500 mb-8">You are not part of any workspace yet. Contact an administrator to get invited.</p>
            </>
          )}
          <button onClick={handleLogout} className="w-full py-3 bg-indigo-600 hover:bg-indigo-700 transition-colors text-white rounded-xl font-medium shadow-lg shadow-indigo-200">
            Back to Login
          </button>
        </div>
      </div>
    );
  }

  const sidebarItems = [
    { id: 'projects', icon: Briefcase, label: 'Projects' },
    { id: 'tasks', icon: CheckSquare, label: 'My Tasks' },
    { id: 'inbox', icon: Inbox, label: 'Inbox' },
    { id: 'reports', icon: BarChart2, label: 'Reports' },
    { id: 'settings', icon: Settings, label: 'Settings' },
  ];

  return (
    <div className="h-screen flex bg-slate-50 overflow-hidden font-sans selection:bg-indigo-100">
      
      {/* Sidebar - Glassmorphism style */}
      <aside className="w-64 flex flex-col bg-slate-900 text-slate-300 border-r border-slate-800 shrink-0 shadow-2xl relative z-20">
        <div className="h-16 flex items-center px-6 border-b border-slate-800 bg-slate-950/50">
          <div className="flex items-center gap-3">
            <div className="bg-gradient-to-tr from-indigo-500 to-purple-500 p-1.5 rounded-lg shadow-lg">
              <LayoutDashboard size={20} className="text-white" />
            </div>
            <h1 className="font-bold text-white text-xl tracking-tight">WorkCollab</h1>
          </div>
        </div>

        <div className="p-4 flex-1 overflow-y-auto">
          <div className="mb-6 px-2">
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3">Workspace</p>
            <select
              value={workspaceId ?? ''}
              onChange={(event) => setSelectedWorkspaceId(event.target.value)}
              className="w-full bg-slate-800/50 p-2.5 rounded-xl border border-slate-700/50 text-slate-200 font-medium outline-none"
              aria-label="Select workspace"
            >
              {workspaces?.map((workspace) => (
                <option key={workspace.id} value={workspace.id} className="bg-slate-900">
                  {workspace.name}
                </option>
              ))}
            </select>
            {isAdmin && (
              <details className="mt-3">
                <summary className="flex cursor-pointer items-center gap-2 text-sm text-indigo-300">
                  <Plus size={15} /> Add workspace
                </summary>
                <form
                  className="mt-3 space-y-2"
                  onSubmit={(event) => {
                    event.preventDefault();
                    createWorkspaceMutation.mutate();
                  }}
                >
                  <input
                    value={workspaceName}
                    onChange={(event) => setWorkspaceName(event.target.value)}
                    placeholder="Workspace name"
                    required
                    className="w-full rounded-lg bg-slate-800 p-2 text-sm text-slate-100 placeholder:text-slate-500"
                  />
                  <select
                    value={managerId}
                    onChange={(event) => setManagerId(event.target.value)}
                    required
                    className="w-full rounded-lg bg-slate-800 p-2 text-sm text-slate-100"
                  >
                    <option value="">
                      {isLoadingManagers ? 'Loading managers...' : 'Assign manager'}
                    </option>
                    {managers.map((manager) => (
                      <option key={manager.id} value={manager.id}>{manager.fullName} ({manager.email})</option>
                    ))}
                  </select>
                  {isManagersError && (
                    <p className="text-xs text-red-300">Unable to load managers. Refresh and try again.</p>
                  )}
                  {!isLoadingManagers && !isManagersError && managers.length === 0 && (
                    <p className="text-xs text-amber-300">No manager accounts are available.</p>
                  )}
                  <button
                    type="submit"
                    disabled={createWorkspaceMutation.isPending || !workspaceName.trim() || !managerId || managers.length === 0}
                    className="w-full rounded-lg bg-indigo-600 p-2 text-sm text-white disabled:opacity-50"
                  >
                    {createWorkspaceMutation.isPending ? 'Creating...' : 'Create workspace'}
                  </button>
                </form>
              </details>
            )}
          </div>

          <nav className="space-y-1">
            {sidebarItems.map(item => (
              <button 
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 ${
                  activeTab === item.id 
                    ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-900/20 font-medium' 
                    : 'hover:bg-slate-800/60 hover:text-slate-100'
                }`}
              >
                <item.icon size={18} className={activeTab === item.id ? 'text-indigo-200' : 'text-slate-400'} />
                {item.label}
              </button>
            ))}
          </nav>
        </div>

        <div className="p-4 border-t border-slate-800 bg-slate-950/30">
          <div className="flex items-center gap-3 px-2 py-2">
            <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-indigo-500 to-pink-500 flex items-center justify-center text-white font-bold shadow-md">
              {user?.fullName?.charAt(0) || 'U'}
            </div>
            <div className="flex-1 overflow-hidden">
              <p className="text-sm font-medium text-slate-200 truncate">{user?.fullName}</p>
              <p className="text-xs text-slate-500 truncate">{user?.email}</p>
            </div>
            <button onClick={handleLogout} className="text-slate-500 hover:text-red-400 transition-colors p-1" title="Logout">
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 bg-slate-50/50">
        
        {/* Top Header */}
        <header className="h-16 bg-white/80 backdrop-blur-md border-b border-gray-200 px-6 flex items-center justify-between shrink-0 sticky top-0 z-10">
          <div>
            {activeTab === 'projects' && (
              <div className="flex items-center gap-3">
                <h2 className="text-xl font-bold text-gray-800">Project Dashboard</h2>
                {projects && projects.length > 0 && (
                  <select
                    value={projectId ?? ''}
                    onChange={(event) => setSelectedProjectId(event.target.value)}
                    className="rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm text-gray-700 outline-none focus:border-indigo-500"
                    aria-label="Select project"
                  >
                    {projects.map((project) => (
                      <option key={project.id} value={project.id}>
                        {project.name}
                      </option>
                    ))}
                  </select>
                )}
                {isManager && workspaceId && (
                  <button
                    type="button"
                    onClick={() => setIsProjectFormOpen((open) => !open)}
                    className="rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700"
                  >
                    {isProjectFormOpen ? 'Cancel' : 'New project'}
                  </button>
                )}
              </div>
            )}
            {activeTab !== 'projects' && (
              <h2 className="text-xl font-bold text-gray-800 capitalize">{activeTab}</h2>
            )}
          </div>
          
          <div className="flex items-center gap-4">
            <PresenceAvatars activeUsers={activeUsers} />
          </div>
        </header>

        {/* Dashboard Content */}
        <main className="flex-1 overflow-hidden relative">
          {isProjectFormOpen && isManager && workspaceId && (
            <form
              className="absolute right-6 top-4 z-10 w-80 space-y-3 rounded-xl border border-gray-200 bg-white p-4 shadow-lg"
              onSubmit={(event) => {
                event.preventDefault();
                createProjectMutation.mutate();
              }}
            >
              <h3 className="font-semibold text-gray-800">Create project</h3>
              <input
                value={projectName}
                onChange={(event) => setProjectName(event.target.value)}
                placeholder="Project name"
                required
                className="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:border-indigo-500"
              />
              <textarea
                value={projectDescription}
                onChange={(event) => setProjectDescription(event.target.value)}
                placeholder="Description (optional)"
                rows={3}
                className="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:border-indigo-500"
              />
              {createProjectMutation.isError && (
                <p className="text-sm text-red-600">Unable to create the project.</p>
              )}
              <button
                type="submit"
                disabled={createProjectMutation.isPending || !projectName.trim()}
                className="w-full rounded-lg bg-indigo-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
              >
                {createProjectMutation.isPending ? 'Creating...' : 'Create project'}
              </button>
            </form>
          )}
          {activeTab === 'projects' ? (
            projectInfo ? (
              <KanbanBoard 
                workspaceId={workspaceId} 
                boardId={projectId!} 
                lists={projectInfo.lists} 
                onTaskClick={setSelectedTask} 
              />
            ) : (
              <div className="h-full flex items-center justify-center">
                <div className="text-center bg-white p-8 rounded-2xl shadow-sm border border-gray-100">
                  <h3 className="text-lg font-medium text-gray-800 mb-2">No Project Selected</h3>
                  <p className="text-gray-500">Create a project to get started</p>
                </div>
              </div>
            )
          ) : activeTab === 'reports' ? (
            projectId ? <ReportsDashboard workspaceId={workspaceId} projectId={projectId} /> : (
              <div className="h-full flex items-center justify-center text-gray-500">
                Select a project first to view analytics.
              </div>
            )
          ) : (
            <div className="h-full flex items-center justify-center">
              <div className="text-center text-gray-400 flex flex-col items-center">
                <LayoutDashboard size={48} className="mb-4 text-gray-300" />
                <p className="text-lg">{activeTab.charAt(0).toUpperCase() + activeTab.slice(1)} module under construction</p>
              </div>
            </div>
          )}
        </main>
      </div>

      {/* Modals */}
      {selectedTask && projectId && (
        <TaskDetailsModal
          workspaceId={workspaceId}
          boardId={projectId}
          task={selectedTask}
          onClose={() => setSelectedTask(null)}
          sendHeartbeat={sendHeartbeat}
        />
      )}
    </div>
  );
}
