package io.github.fabasdev.financas.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuário logado. Os controllers pegam o id daqui (nunca de um parâmetro da requisição),
 * e é esse id que filtra todas as consultas.
 */
public class AppUserPrincipal implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String username;
    private transient String passwordHash;

    public AppUserPrincipal(Long id, String username, String passwordHash) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /** O Spring Security chama depois do login, para o hash não ficar na sessão. */
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    /** Igualdade pelo id: o registro de sessões usa isso para achar as sessões de um usuário. */
    @Override
    public boolean equals(Object o) {
        return o instanceof AppUserPrincipal other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[id=" + id + "]";
    }
}
