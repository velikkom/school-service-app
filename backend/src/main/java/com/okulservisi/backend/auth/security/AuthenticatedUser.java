package com.okulservisi.backend.auth.security;

import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security principal'ı. Controller'lar/service'ler {@code @AuthenticationPrincipal
 * AuthenticatedUser} ile giriş yapan kullanıcının id/rol bilgisine erişir — nesne bazlı
 * yetkilendirme (ownership) kontrollerinin temel girdisidir (bkz. docs/RBAC_MATRIX.md).
 */
public class AuthenticatedUser implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final boolean active;

    public AuthenticatedUser(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.active = user.isActive();
    }

    public Long getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
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
        return active;
    }
}
