package com.workcollab.event;

/**
 * Enumerates all real-time collaboration event types broadcast
 * over WebSocket topics.
 */
public enum CollaborationEventType {

    /** A new task was created on a board. */
    TASK_CREATED,

    /** A task was moved between lists or reordered. */
    TASK_MOVED,

    /** A task's fields (title, description, assignee, etc.) were updated. */
    TASK_UPDATED,

    /** A task was permanently deleted. */
    TASK_DELETED,

    /** A user started viewing, editing, or left a task card. */
    USER_PRESENCE
}
