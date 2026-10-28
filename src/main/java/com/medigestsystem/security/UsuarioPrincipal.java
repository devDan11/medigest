package com.medigestsystem.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuario autenticado en la sesión web. Es serializable porque se guarda en la sesión HTTP.
 * El hash de la contraseña se borra tras autenticar (CredentialsContainer).
 */
public class UsuarioPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private String passwordHash;
    private final boolean activo;
    private final boolean cuentaNoBloqueada;
    private final List<String> roles;

    public UsuarioPrincipal(Long id, String email, String passwordHash, boolean activo,
                            boolean cuentaNoBloqueada, List<String> roles) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.activo = activo;
        this.cuentaNoBloqueada = cuentaNoBloqueada;
        this.roles = List.copyOf(roles);
    }

    public Long getId() {
        return id;
    }

    public List<String> getRoles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
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
        return cuentaNoBloqueada;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
