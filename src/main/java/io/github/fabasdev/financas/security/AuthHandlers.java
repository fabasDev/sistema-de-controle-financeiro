package io.github.fabasdev.financas.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

/** O que acontece depois de cada tentativa de login: conta erro ou zera o contador, e registra no log. */
public final class AuthHandlers {

    private AuthHandlers() {
    }

    /** Erro: sempre a mesma resposta (/login?erro), exista a conta ou não. */
    public static class Failure extends SimpleUrlAuthenticationFailureHandler {

        private final LoginAttemptService attempts;

        public Failure(LoginAttemptService attempts) {
            super("/login?erro");
            this.attempts = attempts;
        }

        @Override
        public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                AuthenticationException exception) throws IOException, ServletException {
            String username = UsernameRules.normalize(request.getParameter("username"));
            attempts.recordFailure(LoginThrottleFilter.SCOPE, username, request.getRemoteAddr());
            SecurityLog.warn("login_falhou", username, request.getRemoteAddr());
            super.onAuthenticationFailure(request, response, exception);
        }
    }

    public static class Success extends SavedRequestAwareAuthenticationSuccessHandler {

        private final LoginAttemptService attempts;
        private final Clock clock;

        public Success(LoginAttemptService attempts, Clock clock) {
            this.attempts = attempts;
            this.clock = clock;
            setDefaultTargetUrl("/");
        }

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                Authentication authentication) throws ServletException, IOException {
            String username = UsernameRules.normalize(request.getParameter("username"));
            attempts.recordSuccess(LoginThrottleFilter.SCOPE, username, request.getRemoteAddr());
            request.getSession().setAttribute(AbsoluteSessionTimeoutFilter.LOGIN_TIME, clock.millis());
            SecurityLog.event("login_ok", username, request.getRemoteAddr());
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
