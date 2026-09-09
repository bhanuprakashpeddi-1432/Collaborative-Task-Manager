export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface Task {
  id: string;
  listId: string;
  title: string;
  description: string;
  priority: Priority;
  position: number;
  dueDate: string | null;
  version: number;
  createdById: string;
  assignedToId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TaskList {
  id: string;
  boardId: string;
  name: string;
  position: number;
}

export interface Board {
  id: string;
  workspaceId: string;
  name: string;
  position: number;
  createdAt: string;
}

export interface CollaborationMessage {
  workspaceId: string;
  boardId: string;
  eventType: 'TASK_CREATED' | 'TASK_MOVED' | 'TASK_UPDATED' | 'TASK_DELETED' | 'USER_PRESENCE';
  payload: any;
  triggeredBy: string;
  timestamp: string;
}

export interface UserPresencePayload {
  userId: string;
  fullName: string;
  avatarUrl?: string;
  taskId: string;
  action: 'VIEWING' | 'EDITING' | 'LEFT';
  timestamp: string;
}
