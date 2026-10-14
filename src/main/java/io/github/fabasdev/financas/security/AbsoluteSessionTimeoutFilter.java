package io.github.fabasdev.financas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tempo máximo de uma sessão logada, mesmo com uso contínuo (Session Management Cheat Sheet).
 * A inatividade (30 min) é tratada pelo próprio servidor. Esta regra encerra a sessão
 * 8 horas depois do login, para uma sessão roubada não valer para sempre.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    /** Atributo da sessão com o instante do login (em milissegundos), gravado por AuthHandlers. */
    public static final String LOGIN_TIME = "loginTime";
    static final Duration MAX_AGE = Duration.ofHours(8);

    private final Clock clock;

    public AbsoluteSessionTimeoutFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(LOGIN_TIME) instanceof Long loginTime
                && clock.millis() - loginTime > MAX_AGE.toMillis()) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            response.sendRedirect(request.getContextPath() + "/login?expirou");
            return;
        }
        chain.doFilter(request, response);
    }
}
