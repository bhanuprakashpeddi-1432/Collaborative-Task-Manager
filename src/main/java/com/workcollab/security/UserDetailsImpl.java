package com.workcollab.security;

import com.workcollab.entity.User;
import com.workcollab.entity.SystemRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserDetailsImpl implements UserDetails {

    private UUID id;
    private String email;
    private String password;
    private String fullName;
    private SystemRole systemRole;

    public static UserDetailsImpl build(User user) {
        return new UserDetailsImpl(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFullName(),
                user.getSystemRole()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = systemRole == SystemRole.ADMIN ? "ROLE_ADMIN"
                : systemRole == SystemRole.MANAGER ? "ROLE_MANAGER" : "ROLE_TEAM_MEMBER";
        return Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
