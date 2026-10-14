package io.github.fabasdev.financas.security;

import io.github.fabasdev.financas.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca o usuário no login. Quando ele não existe, o DaoAuthenticationProvider do Spring
 * ainda confere a senha contra um hash falso, para o tempo de resposta não revelar se a conta existe.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

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
}
