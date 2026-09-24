package com.workcollab.controller;

import com.workcollab.entity.Board;
import com.workcollab.entity.TaskList;
import com.workcollab.entity.Workspace;
import com.workcollab.entity.WorkspaceMember;
import com.workcollab.exception.ResourceNotFoundException;
import com.workcollab.repository.BoardRepository;
import com.workcollab.repository.TaskListRepository;
import com.workcollab.repository.WorkspaceMemberRepository;
import com.workcollab.repository.WorkspaceRepository;
import com.workcollab.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final BoardRepository boardRepository;
    private final TaskListRepository taskListRepository;

    /**
     * Returns all workspaces the authenticated user is a member of.
     */
    @GetMapping("/workspaces")
    public List<Map<String, Object>> getMyWorkspaces(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(userDetails.getId());
        return memberships.stream()
                .map(m -> {
                    Workspace ws = m.getWorkspace();
                    return Map.<String, Object>of(
                            "id", ws.getId(),
                            "name", ws.getName(),
                            "slug", ws.getSlug(),
                            "role", m.getRole().name()
                    );
                })
                .toList();
    }

    /**
     * Returns all boards within a workspace.
     */
    @GetMapping("/workspaces/{workspaceId}/boards")
    public List<Map<String, Object>> getBoards(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        // Verify membership
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found or access denied"));

        List<Board> boards = boardRepository.findByWorkspaceIdOrderByPositionAsc(workspaceId);
        return boards.stream()
                .map(b -> Map.<String, Object>of(
                        "id", b.getId(),
                        "workspaceId", b.getWorkspace().getId(),
                        "name", b.getName(),
                        "position", b.getPosition(),
                        "createdAt", b.getCreatedAt().toString()
                ))
                .toList();
    }

    /**
     * Returns board details along with its lists.
     */
    @GetMapping("/boards/{boardId}")
    public Map<String, Object> getBoardWithLists(
            @PathVariable UUID boardId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Board not found"));

        // Verify membership
        workspaceMemberRepository.findByWorkspaceIdAndUserId(board.getWorkspace().getId(), userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Access denied"));

        List<TaskList> lists = taskListRepository.findByBoardIdOrderByPositionAsc(boardId);

        List<Map<String, Object>> listDtos = lists.stream()
                .map(l -> Map.<String, Object>of(
                        "id", l.getId(),
                        "boardId", l.getBoard().getId(),
                        "name", l.getName(),
                        "position", l.getPosition()
                ))
                .toList();

        Map<String, Object> boardDto = Map.of(
                "id", board.getId(),
                "workspaceId", board.getWorkspace().getId(),
                "name", board.getName(),
                "position", board.getPosition(),
                "createdAt", board.getCreatedAt().toString()
        );

        return Map.of(
                "board", boardDto,
                "lists", listDtos
        );
    }
}
