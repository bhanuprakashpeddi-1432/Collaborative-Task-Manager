package com.workcollab.exception;

import lombok.Getter;

@Getter
public class TaskOptimisticLockingException extends RuntimeException {
    
    private final Long staleVersion;
    private final Long currentVersion;

    public TaskOptimisticLockingException(String message, Long staleVersion, Long currentVersion) {
        super(message);
        this.staleVersion = staleVersion;
        this.currentVersion = currentVersion;
    }
}
