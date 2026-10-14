package io.github.fabasdev.financas.security;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

/** Encerra as sessões abertas de um usuário (ex.: depois de trocar a senha). */
@Component
public class UserSessions {

    private final SessionRegistry registry;

    public UserSessions(SessionRegistry registry) {
        this.registry = registry;
    }

    /** Encerra todas as sessões do usuário, menos a de {@code keepSessionId} (pode ser null). */
    public void expireAll(Long userId, String keepSessionId) {
        for (Object principal : registry.getAllPrincipals()) {
            if (principal instanceof AppUserPrincipal p && p.getId().equals(userId)) {
                for (SessionInformation info : registry.getAllSessions(principal, false)) {
                    if (!info.getSessionId().equals(keepSessionId)) {
                        info.expireNow();
                    }
                }
            }
        }
    }
}
