package io.github.fabasdev.financas.service.undo;

import java.io.Serializable;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/**
 * Guarda na sessão do usuário só o último "desfazer". Ele vale uma vez, por {@link #VALIDITY},
 * e só para o mesmo usuário. Como fica na sessão, outra pessoa não tem como usá-lo.
 */
@Component
@SessionScope
public class UndoStore implements Serializable {

    public static final Duration VALIDITY = Duration.ofMinutes(10);

    /**
     * @param returnTo página para onde voltar depois de desfazer (montada pelo servidor, nunca pela requisição)
     */
    public record Entry(String token, Long userId, Instant expiresAt, String returnTo, UndoAction action)
            implements Serializable {
    }

    private final transient Clock clock;
    private Entry entry;

    public UndoStore(Clock clock) {
        this.clock = clock;
    }

    /** Guarda a ação (substitui a anterior) e devolve o token que vai no botão "Desfazer". */
    public synchronized String offer(Long userId, UndoAction action, String returnTo) {
        String token = UUID.randomUUID().toString();
        entry = new Entry(token, userId, now().plus(VALIDITY), returnTo, action);
        return token;
    }

    /** Tira a ação da sessão (uso único). Vazio se o token não confere, é de outro usuário ou venceu. */
    public synchronized Optional<Entry> take(Long userId, String token) {
        Entry current = entry;
        if (current == null || token == null
                || !MessageDigest.isEqual(current.token().getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))
                || !current.userId().equals(userId)) {
            return Optional.empty();
        }
        entry = null;
        return current.expiresAt().isAfter(now()) ? Optional.of(current) : Optional.empty();
    }

    /** O relógio não vai junto se a sessão for serializada; aí usa o do sistema. */
    private Instant now() {
        return (clock != null ? clock : Clock.systemUTC()).instant();
    }
}
