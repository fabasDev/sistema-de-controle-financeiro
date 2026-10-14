package io.github.fabasdev.financas.config;

import io.github.fabasdev.financas.security.AbsoluteSessionTimeoutFilter;
import io.github.fabasdev.financas.security.AuthHandlers;
import io.github.fabasdev.financas.security.LoginAttemptService;
import io.github.fabasdev.financas.security.LoginThrottleFilter;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.HeaderWriterLogoutHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter.Directive;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
public class SecurityConfig {

    /**
     * Só arquivos do próprio app: nada de script ou estilo inline, de outro site ou dentro de iframe.
     * É o que torna um XSS bem mais difícil de explorar.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self'",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'none'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    /** Páginas abertas sem login. */
    private static final String[] PUBLIC_PATHS = {
            "/login", "/cadastro", "/cadastro/codigo", "/recuperar", "/recuperar/codigo", "/privacidade", "/error",
            "/css/**", "/js/**", "/fonts/**", "/img/**", "/favicon.svg", "/robots.txt"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptService attempts, Clock clock,
            SessionRegistry sessionRegistry, @Value("${server.servlet.session.cookie.name}") String sessionCookie)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(new AuthHandlers.Success(attempts, clock))
                        .failureHandler(new AuthHandlers.Failure(attempts))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .addLogoutHandler(new CookieClearingLogoutHandler(expiredCookie(sessionCookie)))
                        // Pede ao navegador para apagar cache e cookies do site (só em HTTPS).
                        .addLogoutHandler(new HeaderWriterLogoutHandler(
                                new ClearSiteDataHeaderWriter(Directive.CACHE, Directive.COOKIES, Directive.STORAGE)))
                        .logoutSuccessUrl("/login?saiu"))
                .sessionManagement(session -> session
                        // Novo id de sessão no login (contra fixação de sessão).
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        // Sem limite de aparelhos, mas registrando as sessões para poder encerrá-las
                        // quando a senha muda.
                        .sessionConcurrency(concurrency -> concurrency
                                .maximumSessions(-1)
                                .sessionRegistry(sessionRegistry)
                                .expiredUrl("/login?expirou")))
                // CSRF fica ligado (padrão). O Thymeleaf coloca o token em todo formulário th:action.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        // HSTS de 2 anos. O Spring só envia em HTTPS.
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(63_072_000).includeSubDomains(true))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny())
                        .permissionsPolicyHeader(permissions -> permissions
                                .policy("camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                        .crossOriginOpenerPolicy(coop -> coop.policy(CrossOriginOpenerPolicy.SAME_ORIGIN)))
                .addFilterBefore(new LoginThrottleFilter(attempts), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new AbsoluteSessionTimeoutFilter(clock), SecurityContextHolderFilter.class);
        return http.build();
    }

    /**
     * BCrypt com o prefixo do algoritmo ({bcrypt}$2a$12$...). Se um dia o padrão mudar para Argon2,
     * as senhas antigas continuam funcionando e são refeitas no próximo login.
     */
    @Bean
    public PasswordEncoder passwordEncoder(@Value("${app.security.bcrypt-strength:12}") int strength) {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(strength)));
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Avisa o registro de sessões quando uma sessão expira, para ele não guardar sessões mortas. */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /** Cookie vencido com os mesmos atributos do original. O prefixo __Host- exige Secure e Path=/. */
    private static Cookie expiredCookie(String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        return cookie;
    }
}
