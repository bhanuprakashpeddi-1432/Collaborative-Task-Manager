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
  const [taskTitle, setTaskTitle] = useState('');
  const [taskPriority, setTaskPriority] = useState<'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'>('MEDIUM');
  const [taskProjectId, setTaskProjectId] = useState('');
  const [taskListId, setTaskListId] = useState('');
  const [taskAssigneeId, setTaskAssigneeId] = useState('');
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

  const { data: taskProjectInfo } = useQuery<{ project: BoardType; lists: TaskList[] }>({
    queryKey: ['task-project', taskProjectId],
    queryFn: async () => (await api.get(`/projects/${taskProjectId}`)).data,
    enabled: !!taskProjectId && activeTab === 'tasks',
  });

  const { data: workspaceMembers = [] } = useQuery<{ id: string; fullName: string; email: string; role: string }[]>({
    queryKey: ['workspace-members', workspaceId],
    queryFn: async () => (await api.get(`/workspaces/${workspaceId}/members`)).data,
    enabled: !!workspaceId && activeTab === 'tasks' && isManager,
  });

  const { data: myTasks = [], isLoading: isLoadingMyTasks } = useQuery<Task[]>({
    queryKey: ['my-tasks', workspaceId, user?.id, user?.systemRole],
    queryFn: async () => {
      if (!workspaceId || !projects?.length) return [];
      const taskResponses = await Promise.all(projects.map(async (project) => {
        const assigneeFilter = user?.systemRole === 'TEAM_MEMBER' ? `&assignedToId=${user.id}` : '';
        const response = await api.get(`/projects/${project.id}/tasks?workspaceId=${workspaceId}&size=1000${assigneeFilter}`);
        return (response.data.content || response.data) as Task[];
      }));
      return taskResponses.flat();
    },
    enabled: !!workspaceId && !!projects?.length && activeTab === 'tasks',
  });

  const createMyTaskMutation = useMutation({
    mutationFn: async () => (await api.post(`/projects/${taskProjectId}/tasks?workspaceId=${workspaceId}`, {
      projectId: taskProjectId,
      listId: taskListId,
      title: taskTitle,
      priority: taskPriority,
      assignedToId: taskAssigneeId || null,
    })).data,
    onSuccess: async () => {
      setTaskTitle('');
      setTaskAssigneeId('');
      setTaskPriority('MEDIUM');
      await queryClient.invalidateQueries({ queryKey: ['my-tasks', workspaceId] });
      await queryClient.invalidateQueries({ queryKey: ['tasks', taskProjectId] });
    },
  });

  useEffect(() => {
    if (projectId && !taskProjectId) setTaskProjectId(projectId);
  }, [projectId, taskProjectId]);

  useEffect(() => {
    if (taskProjectInfo?.lists?.length && (!taskListId || !taskProjectInfo.lists.some((list) => list.id === taskListId))) {
      setTaskListId(taskProjectInfo.lists[0].id);
    }
  }, [taskProjectInfo, taskListId]);

  // Use WebSocket for real-time sync and presence
  const { activeUsers, sendHeartbeat } = useBoardWebSocket(workspaceId ?? '', projectId ?? '');

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const isLoading = isLoadingWorkspaces || isLoadingProjects || isLoadingProject;

  if (isLoading) {
    return (
      <div className="h-screen flex items-center justify-center bg-stone-50">
        <div className="flex flex-col items-center gap-3 rounded-2xl border border-stone-200 bg-white p-8 shadow-[0_16px_50px_rgba(19,78,74,0.08)]">
          <Loader2 size={36} className="animate-spin text-teal-800" />
          <span className="font-medium tracking-wide text-teal-950">Opening your workspace...</span>
        </div>
      </div>
    );
  }

  if (!workspaceId) {
    return (
      <div className="h-screen flex items-center justify-center bg-stone-50 px-5">
        <div className="w-full max-w-md border border-stone-200 bg-white p-10 text-center shadow-[0_16px_50px_rgba(19,78,74,0.08)]">
          {isAdmin ? (
            <>
              <h2 className="mb-3 text-2xl font-semibold tracking-tight text-teal-950">Create a workspace</h2>
              <p className="mb-6 text-stone-600">Create a workspace and assign it to a manager.</p>
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
                  className="w-full rounded-md border border-stone-300 px-4 py-3 outline-none focus:border-teal-800 focus:ring-2 focus:ring-teal-800/15"
                />
                <select
                  value={managerId}
                  onChange={(event) => setManagerId(event.target.value)}
                  required
                  className="w-full rounded-md border border-stone-300 px-4 py-3 outline-none focus:border-teal-800 focus:ring-2 focus:ring-teal-800/15"
                >
                  <option value="">
                    {isLoadingManagers ? 'Loading managers...' : 'Select manager'}
                  </option>
                  {managers.map((manager) => (
                    <option key={manager.id} value={manager.id}>{manager.fullName} ({manager.email})</option>
                  ))}
                </select>
                {isManagersError && (
                  <p className="text-sm text-red-700">Unable to load the manager list. Please refresh and try again.</p>
                )}
                {!isLoadingManagers && !isManagersError && managers.length === 0 && (
                  <p className="text-sm text-amber-700">No manager accounts are available.</p>
                )}
                {createWorkspaceMutation.isError && (
                  <p className="text-sm text-red-700">Unable to create workspace. Check the name and manager.</p>
                )}
                <button
                  type="submit"
                  disabled={createWorkspaceMutation.isPending || !workspaceName.trim() || !managerId || managers.length === 0}
                  className="w-full rounded-md bg-teal-900 py-3 font-medium text-white transition-colors hover:bg-teal-800 disabled:opacity-50"
                >
                  {createWorkspaceMutation.isPending ? 'Creating...' : 'Create workspace'}
                </button>
              </form>
            </>
          ) : (
            <>
              <h2 className="mb-3 text-2xl font-semibold tracking-tight text-teal-950">Welcome to WorkCollab</h2>
              <p className="mb-8 text-stone-600">You are not part of any workspace yet. Contact an administrator to get invited.</p>
            </>
          )}
          <button onClick={handleLogout} className="w-full rounded-md bg-teal-900 py-3 font-medium text-white transition-colors hover:bg-teal-800">
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
    <div className="h-screen flex overflow-hidden bg-stone-50 font-sans selection:bg-teal-100">
      
      {/* Sidebar - Glassmorphism style */}
      <aside className="relative z-20 flex w-64 shrink-0 flex-col border-r border-teal-900 bg-teal-950 text-teal-100 shadow-2xl">
        <div className="flex h-16 items-center border-b border-teal-900 px-6">
          <div className="flex items-center gap-3">
            <div className="grid h-8 w-8 place-items-center rounded bg-teal-50 text-teal-950">
              <LayoutDashboard size={18} />
            </div>
            <h1 className="text-xl font-semibold tracking-tight text-white">WorkCollab</h1>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-4">
          <div className="mb-6 px-2">
            <p className="mb-3 px-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-teal-400">Workspace</p>
            <select
              value={workspaceId ?? ''}
              onChange={(event) => setSelectedWorkspaceId(event.target.value)}
              className="w-full rounded-md border border-teal-800 bg-teal-900/60 p-2.5 font-medium text-teal-50 outline-none focus:border-teal-500"
              aria-label="Select workspace"
            >
              {workspaces?.map((workspace) => (
                <option key={workspace.id} value={workspace.id} className="bg-teal-950">
                  {workspace.name}
                </option>
              ))}
            </select>
            {isAdmin && (
              <details className="mt-3">
                <summary className="flex cursor-pointer items-center gap-2 text-sm text-teal-300">
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
                    className="w-full rounded-md border border-teal-800 bg-teal-900 p-2 text-sm text-teal-50 placeholder:text-teal-500"
                  />
                  <select
                    value={managerId}
                    onChange={(event) => setManagerId(event.target.value)}
                    required
                    className="w-full rounded-md border border-teal-800 bg-teal-900 p-2 text-sm text-teal-50"
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
                    className="w-full rounded-md bg-teal-700 p-2 text-sm text-white hover:bg-teal-600 disabled:opacity-50"
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
                    ? 'bg-teal-50 text-teal-950 shadow-lg shadow-teal-950/20 font-medium'
                    : 'text-teal-300 hover:bg-teal-900 hover:text-white'
                }`}
              >
                <item.icon size={18} className={activeTab === item.id ? 'text-teal-800' : 'text-teal-500'} />
                {item.label}
              </button>
            ))}
          </nav>
        </div>

        <div className="border-t border-teal-900 p-4">
          <div className="flex items-center gap-3 px-2 py-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-teal-700 font-bold text-white shadow-md">
              {user?.fullName?.charAt(0) || 'U'}
            </div>
            <div className="flex-1 overflow-hidden">
              <p className="truncate text-sm font-medium text-teal-50">{user?.fullName}</p>
              <p className="truncate text-xs text-teal-400">{user?.email}</p>
            </div>
            <button onClick={handleLogout} className="p-1 text-teal-400 transition-colors hover:text-red-300" title="Logout">
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex min-w-0 flex-1 flex-col bg-stone-50">
        
        {/* Top Header */}
        <header className="sticky top-0 z-10 flex h-[4.5rem] shrink-0 items-center justify-between border-b border-stone-200 bg-stone-50/95 px-6 backdrop-blur-md">
          <div>
            {activeTab === 'projects' && (
              <div className="flex items-center gap-3">
                <div>
                  <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-teal-700">Current workspace</p>
                  <h2 className="text-xl font-semibold tracking-tight text-teal-950">Project dashboard</h2>
                </div>
                {projects && projects.length > 0 && (
                  <select
                    value={projectId ?? ''}
                    onChange={(event) => setSelectedProjectId(event.target.value)}
                    className="rounded-md border border-stone-300 bg-white px-3 py-2 text-sm text-stone-700 outline-none focus:border-teal-800 focus:ring-2 focus:ring-teal-800/15"
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
                    className="rounded-md bg-teal-900 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-teal-800"
                  >
                    {isProjectFormOpen ? 'Cancel' : 'New project'}
                  </button>
                )}
              </div>
            )}
            {activeTab !== 'projects' && (
              <div>
                <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-teal-700">Workspace view</p>
                <h2 className="text-xl font-semibold capitalize tracking-tight text-teal-950">{activeTab}</h2>
              </div>
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
              className="absolute right-6 top-4 z-10 w-80 space-y-3 rounded-xl border border-stone-200 bg-white p-4 shadow-[0_16px_40px_rgba(19,78,74,0.12)]"
              onSubmit={(event) => {
                event.preventDefault();
                createProjectMutation.mutate();
              }}
            >
              <h3 className="font-semibold text-teal-950">Create project</h3>
              <input
                value={projectName}
                onChange={(event) => setProjectName(event.target.value)}
                placeholder="Project name"
                required
                className="w-full rounded-md border border-stone-300 px-3 py-2 text-sm outline-none focus:border-teal-800 focus:ring-2 focus:ring-teal-800/15"
              />
              <textarea
                value={projectDescription}
                onChange={(event) => setProjectDescription(event.target.value)}
                placeholder="Description (optional)"
                rows={3}
                className="w-full rounded-md border border-stone-300 px-3 py-2 text-sm outline-none focus:border-teal-800 focus:ring-2 focus:ring-teal-800/15"
              />
              {createProjectMutation.isError && (
                <p className="text-sm text-red-700">Unable to create the project.</p>
              )}
              <button
                type="submit"
                disabled={createProjectMutation.isPending || !projectName.trim()}
                className="w-full rounded-md bg-teal-900 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-teal-800 disabled:opacity-50"
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
              <div className="flex h-full items-center justify-center p-6">
              <div className="max-w-sm border border-stone-200 bg-white p-8 text-center shadow-[0_16px_40px_rgba(19,78,74,0.08)]">
                <div className="mx-auto mb-5 grid h-12 w-12 place-items-center rounded-full bg-teal-50 text-teal-800">
                  <Briefcase size={21} />
                </div>
                <h3 className="mb-2 text-lg font-semibold text-teal-950">A clear desk awaits</h3>
                <p className="text-sm leading-6 text-stone-600">Create a project to turn this workspace into your team&apos;s next shared board.</p>
              </div>
              </div>
            )
          ) : activeTab === 'tasks' ? (
            <div className="h-full overflow-y-auto p-6">
              <div className="mx-auto max-w-5xl space-y-6">
                {isManager && (
                  <form
                    className="rounded-xl border border-stone-200 bg-white p-5 shadow-sm"
                    onSubmit={(event) => {
                      event.preventDefault();
                      createMyTaskMutation.mutate();
                    }}
                  >
                    <h3 className="mb-4 text-lg font-semibold text-teal-950">Create and assign a task</h3>
                    <div className="grid gap-3 md:grid-cols-4">
                      <input
                        value={taskTitle}
                        onChange={(event) => setTaskTitle(event.target.value)}
                        placeholder="Task title"
                        required
                        className="rounded-lg border border-stone-300 px-3 py-2 text-sm outline-none focus:border-teal-800"
                      />
                      <select value={taskPriority} onChange={(event) => setTaskPriority(event.target.value as typeof taskPriority)} className="rounded-lg border border-stone-300 px-3 py-2 text-sm">
                        <option value="LOW">Low priority</option>
                        <option value="MEDIUM">Medium priority</option>
                        <option value="HIGH">High priority</option>
                        <option value="URGENT">Urgent priority</option>
                      </select>
                      <select value={taskProjectId} onChange={(event) => setTaskProjectId(event.target.value)} required className="rounded-lg border border-stone-300 px-3 py-2 text-sm">
                        <option value="">Select project</option>
                        {projects?.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
                      </select>
                      <select value={taskListId} onChange={(event) => setTaskListId(event.target.value)} required className="rounded-lg border border-stone-300 px-3 py-2 text-sm">
                        <option value="">Select column</option>
                        {taskProjectInfo?.lists.map((list) => <option key={list.id} value={list.id}>{list.name}</option>)}
                      </select>
                      <select value={taskAssigneeId} onChange={(event) => setTaskAssigneeId(event.target.value)} required className="rounded-lg border border-stone-300 px-3 py-2 text-sm">
                        <option value="">Assign team member</option>
                        {workspaceMembers.filter((member) => member.role === 'TEAM_MEMBER').map((member) => (
                          <option key={member.id} value={member.id}>{member.fullName} ({member.email})</option>
                        ))}
                      </select>
                    </div>
                    {createMyTaskMutation.isError && <p className="mt-3 text-sm text-red-600">Unable to create the assigned task.</p>}
                    <button type="submit" disabled={createMyTaskMutation.isPending || !taskTitle.trim() || !taskProjectId || !taskListId || !taskAssigneeId} className="mt-4 rounded-lg bg-teal-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50">
                      {createMyTaskMutation.isPending ? 'Creating...' : 'Create task'}
                    </button>
                  </form>
                )}
                <section className="rounded-xl border border-stone-200 bg-white shadow-sm">
                  <div className="border-b border-stone-200 p-5">
                    <h3 className="text-lg font-semibold text-teal-950">{isManager ? 'Workspace tasks' : 'Tasks assigned to me'}</h3>
                  </div>
                  {isLoadingMyTasks ? (
                    <div className="p-5 text-sm text-stone-500">Loading tasks...</div>
                  ) : myTasks.length === 0 ? (
                    <div className="p-5 text-sm text-stone-500">{isManager ? 'No tasks have been created yet.' : 'No tasks have been assigned to you.'}</div>
                  ) : (
                    <div className="divide-y divide-stone-100">
                      {myTasks.map((task) => (
                        <button
                          key={task.id}
                          type="button"
                          onClick={() => {
                            setSelectedProjectId(task.projectId);
                            setActiveTab('projects');
                            setSelectedTask(task);
                          }}
                          className="flex w-full items-center justify-between p-4 text-left hover:bg-stone-50"
                        >
                          <span className="font-medium text-stone-800">{task.title}</span>
                          <span className="text-xs text-stone-500">{task.status} · {task.priority}</span>
                        </button>
                      ))}
                    </div>
                  )}
                </section>
              </div>
            </div>
          ) : activeTab === 'reports' ? (
            projectId ? <ReportsDashboard workspaceId={workspaceId} projectId={projectId} /> : (
              <div className="flex h-full items-center justify-center text-stone-500">
                Select a project first to view analytics.
              </div>
            )
          ) : (
            <div className="h-full flex items-center justify-center">
              <div className="flex flex-col items-center text-center text-stone-400">
                <LayoutDashboard size={48} className="mb-4 text-teal-200" />
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
