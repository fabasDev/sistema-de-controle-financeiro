package io.github.fabasdev.financas.security;

import io.github.fabasdev.financas.domain.User;
import io.github.fabasdev.financas.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca o usuário no login. Quando ele não existe, o DaoAuthenticationProvider do Spring
 * ainda confere a senha contra um hash falso, para o tempo de resposta não revelar se a conta existe.
 *
 * <p>Também refaz o hash: quando a senha confere com um hash de um algoritmo antigo (BCrypt), o
 * Spring chama {@link #updatePassword} com o hash novo (Argon2id), e ele é gravado no lugar.
 */
@Service
public class AppUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(UsernameRules.normalize(username))
                .map(u -> new AppUserPrincipal(u.getId(), u.getUsername(), u.getPasswordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("usuário ou senha incorretos"));
    }

    /** {@code newPassword} já chega como hash (nunca a senha digitada). */
    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        AppUserPrincipal principal = (AppUserPrincipal) user;
        User entity = users.findById(principal.getId()).orElseThrow();
        entity.setPasswordHash(newPassword);
        return new AppUserPrincipal(entity.getId(), entity.getUsername(), newPassword);
    }
}
