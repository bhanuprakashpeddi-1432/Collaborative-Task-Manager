package com.workcollab.controller;

import com.workcollab.dto.JwtAuthResponse;
import com.workcollab.dto.LoginRequest;
import com.workcollab.security.JwtUtils;
import com.workcollab.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final com.workcollab.repository.UserRepository userRepository;
    private final com.workcollab.repository.WorkspaceRepository workspaceRepository;
    private final com.workcollab.repository.WorkspaceMemberRepository workspaceMemberRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<JwtAuthResponse> authenticateUser(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        JwtAuthResponse.UserDto userDto = new JwtAuthResponse.UserDto(
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getFullName()
        );

        return ResponseEntity.ok(new JwtAuthResponse(jwt, userDto));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody com.workcollab.dto.RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Error: Email is already in use!"));
        }

        // Create new user's account
        com.workcollab.entity.User user = com.workcollab.entity.User.builder()
                .email(registerRequest.getEmail())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .fullName(registerRequest.getFullName())
                .build();

        user = userRepository.save(user);

        // Create a default workspace for the user
        com.workcollab.entity.Workspace workspace = com.workcollab.entity.Workspace.builder()
                .name(user.getFullName() + "'s Workspace")
                .slug(user.getFullName().toLowerCase().replaceAll("\\s+", "-") + "-workspace-" + java.util.UUID.randomUUID().toString().substring(0, 5))
                .owner(user)
                .build();
        workspace = workspaceRepository.save(workspace);

        // Add user as admin to their new workspace
        com.workcollab.entity.WorkspaceMember member = com.workcollab.entity.WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(com.workcollab.entity.Role.ADMIN)
                .build();
        workspaceMemberRepository.save(member);

        // Authenticate the newly registered user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(registerRequest.getEmail(), registerRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        JwtAuthResponse.UserDto userDto = new JwtAuthResponse.UserDto(
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getFullName()
        );

        return ResponseEntity.ok(new JwtAuthResponse(jwt, userDto));
    }
}
