export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface Task {
  id: string;
  projectId: string;
  listId: string | null;
  sprintId: string | null;
  parentTaskId: string | null;
  title: string;
  description: string;
  status: string;
  priority: Priority;
  position: number;
  estimatedHours: number | null;
  actualHours: number | null;
  progress: number;
  dueDate: string | null;
  version: number;
  createdById: string;
  assignedToId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TaskActivity {
  id: string;
  userId: string | null;
  userName: string;
  actionType: string;
  details: string;
  createdAt: string;
}

export interface InboxContact {
  id: string;
  fullName: string;
  email: string;
  systemRole: 'ADMIN' | 'MANAGER' | 'TEAM_MEMBER';
}

export interface DirectMessage {
  id: string;
  senderId: string;
  senderName: string;
  recipientId: string;
  recipientName: string;
  otherUserId: string;
  otherUserName: string;
  subject: string;
  content: string;
  read: boolean;
  createdAt: string;
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

export interface Comment {
  id: string;
  taskId: string;
  userId: string;
  userName: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface CollaborationMessage {
  workspaceId: string;
  projectId: string;
  eventType: 'TASK_CREATED' | 'TASK_MOVED' | 'TASK_UPDATED' | 'TASK_DELETED' | 'USER_PRESENCE' | 'COMMENT_ADDED';
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
