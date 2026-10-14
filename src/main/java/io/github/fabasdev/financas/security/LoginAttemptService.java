package io.github.fabasdev.financas.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Limite de tentativas no servidor (o do app antigo ficava no navegador e dava para zerar).
 * Conta erros em três chaves, como recomenda o Authentication Cheat Sheet da OWASP:
 * <ul>
 *   <li>usuário + IP: bloqueia a partir do 5º erro;</li>
 *   <li>só usuário: pega ataque vindo de muitos IPs (a partir do 20º erro);</li>
 *   <li>só IP: pega um IP testando muitos usuários (a partir do 50º erro).</li>
 * </ul>
 * A espera começa em 30 s e dobra a cada erro, até 15 min. Vale para qualquer nome digitado,
 * exista a conta ou não, então o bloqueio não revela quem tem conta.
 * Fica em memória: serve para um servidor só. Com vários, teria que ir para o banco.
 */
@Component
public class LoginAttemptService {

    static final int PAIR_THRESHOLD = 5;
    static final int USER_THRESHOLD = 20;
    static final int IP_THRESHOLD = 50;
    static final Duration BASE_DELAY = Duration.ofSeconds(30);
    static final Duration MAX_DELAY = Duration.ofMinutes(15);
    /** Sem erro nesse tempo (e fora do bloqueio), o contador zera. */
    static final Duration WINDOW = Duration.ofHours(1);
    /** Limite de memória: guarda no máximo esse número de contadores e esquece os mais antigos. */
    static final int MAX_ENTRIES = 20_000;

    private record Counter(int failures, Instant lastFailure, Instant blockedUntil) {
    }

    private final Clock clock;
    private final Map<String, Counter> counters = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Counter> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** Quanto falta para poder tentar de novo, ou vazio se não está bloqueado. */
    public synchronized Optional<Duration> blockedFor(String scope, String username, String ip) {
        Instant now = clock.instant();
        Duration longest = Duration.ZERO;
        for (String key : keys(scope, username, ip)) {
            Counter c = current(key, now);
            if (c != null && c.blockedUntil() != null && c.blockedUntil().isAfter(now)) {
                Duration left = Duration.between(now, c.blockedUntil());
                if (left.compareTo(longest) > 0) {
                    longest = left;
                }
            }
        }
        return longest.isZero() ? Optional.empty() : Optional.of(longest);
    }

    public synchronized void recordFailure(String scope, String username, String ip) {
        Instant now = clock.instant();
        String[] keys = keys(scope, username, ip);
        int[] thresholds = {PAIR_THRESHOLD, USER_THRESHOLD, IP_THRESHOLD};
        for (int i = 0; i < keys.length; i++) {
            Counter c = current(keys[i], now);
            int failures = c == null ? 1 : c.failures() + 1;
            Instant blockedUntil = failures >= thresholds[i] ? now.plus(delay(failures - thresholds[i])) : null;
            counters.put(keys[i], new Counter(failures, now, blockedUntil));
        }
    }

    /** Limite simples por IP para ações sem senha (ex.: cadastro): espera crescente a partir de {@code threshold}. */
    public synchronized Optional<Duration> blockedForIp(String scope, String ip) {
        Instant now = clock.instant();
        Counter c = current(scope + "|ip|" + ip, now);
        if (c == null || c.blockedUntil() == null || !c.blockedUntil().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, c.blockedUntil()));
    }

    public synchronized void recordForIp(String scope, String ip, int threshold) {
        Instant now = clock.instant();
        String key = scope + "|ip|" + ip;
        Counter c = current(key, now);
        int count = c == null ? 1 : c.failures() + 1;
        counters.put(key, new Counter(count, now, count >= threshold ? now.plus(delay(count - threshold)) : null));
    }

    /** Acertou: zera os contadores do usuário. O do IP continua, para não ajudar quem testa vários usuários. */
    public synchronized void recordSuccess(String scope, String username, String ip) {
        String[] keys = keys(scope, username, ip);
        counters.remove(keys[0]);
        counters.remove(keys[1]);
    }

    private Counter current(String key, Instant now) {
        Counter c = counters.get(key);
        if (c == null) {
            return null;
        }
        boolean blocked = c.blockedUntil() != null && c.blockedUntil().isAfter(now);
        if (!blocked && c.lastFailure().plus(WINDOW).isBefore(now)) {
            counters.remove(key);
            return null;
        }
        return c;
    }

    /** 30 s, 1 min, 2 min, 4 min, 8 min, 15 min, 15 min... */
    static Duration delay(int extraFailures) {
        int shift = Math.min(extraFailures, 10);
        Duration d = BASE_DELAY.multipliedBy(1L << shift);
        return d.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : d;
    }

    private static String[] keys(String scope, String username, String ip) {
        return new String[] {
                scope + "|pair|" + username + "|" + ip,
                scope + "|user|" + username,
                scope + "|ip|" + ip};
    }
}
