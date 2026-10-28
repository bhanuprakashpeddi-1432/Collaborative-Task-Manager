package com.workcollab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class DirectMessageRequest {

    @NotNull
    private UUID recipientId;

    @NotBlank
    @Size(max = 200)
    private String subject;

    @NotBlank
    @Size(max = 10000)
    private String content;
}
