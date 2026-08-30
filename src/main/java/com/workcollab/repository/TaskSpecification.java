package com.workcollab.repository;

import com.workcollab.entity.Priority;
import com.workcollab.entity.Task;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TaskSpecification {

    public static Specification<Task> filterTasks(
            UUID boardId,
            Priority priority,
            UUID assignedToId,
            ZonedDateTime dueDateStart,
            ZonedDateTime dueDateEnd,
            String searchString
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Must belong to the board (via list)
            predicates.add(cb.equal(root.join("list").get("board").get("id"), boardId));

            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }

            if (assignedToId != null) {
                predicates.add(cb.equal(root.join("assignedTo").get("id"), assignedToId));
            }

            if (dueDateStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), dueDateStart));
            }

            if (dueDateEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), dueDateEnd));
            }

            if (StringUtils.hasText(searchString)) {
                String likePattern = "%" + searchString.toLowerCase() + "%";
                Predicate titlePredicate = cb.like(cb.lower(root.get("title")), likePattern);
                Predicate descPredicate = cb.like(cb.lower(root.get("description")), likePattern);
                predicates.add(cb.or(titlePredicate, descPredicate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
