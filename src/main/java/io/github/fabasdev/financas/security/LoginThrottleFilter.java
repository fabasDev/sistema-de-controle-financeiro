package io.github.fabasdev.financas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Roda antes do login do Spring Security (POST /login):
 * <ul>
 *   <li>recusa na hora quem está bloqueado por excesso de erros, sem nem conferir a senha;</li>
 *   <li>recusa usuário ou senha grandes demais. A senha acima de 72 bytes nunca chega ao BCrypt,
 *       que cortaria o resto sem avisar.</li>
 * </ul>
 * O IP vem de getRemoteAddr(). O X-Forwarded-For não é lido de propósito: qualquer um pode
 * inventar esse cabeçalho. Atrás de um proxy, configure server.forward-headers-strategy.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    public static final String SCOPE = "login";

    private final LoginAttemptService attempts;

    public LoginThrottleFilter(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Compara pelo URI: o servletPath pode vir vazio, conforme o mapeamento do servlet.
        return !("POST".equals(request.getMethod())
                && (request.getContextPath() + "/login").equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rawUsername = request.getParameter("username");
        String password = request.getParameter("password");
        String username = UsernameRules.normalize(rawUsername);
        String ip = request.getRemoteAddr();

        if (attempts.blockedFor(SCOPE, username, ip).isPresent()) {
            SecurityLog.warn("login_bloqueado", username, ip);
            response.sendRedirect(request.getContextPath() + "/login?bloqueado");
            return;
        }
        boolean invalid = rawUsername == null || password == null || username.isEmpty()
                || username.length() > UsernameRules.MAX || PasswordPolicy.exceedsMaxBytes(password);
        if (invalid) {
            attempts.recordFailure(SCOPE, username, ip);
            SecurityLog.warn("login_falhou", username, ip);
            response.sendRedirect(request.getContextPath() + "/login?erro");
            return;
        }
        chain.doFilter(request, response);
    }
}
