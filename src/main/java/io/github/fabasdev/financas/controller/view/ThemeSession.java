package io.github.fabasdev.financas.controller.view;

import io.github.fabasdev.financas.domain.Theme;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * O tema de quem está logado, para o layout. É lido do banco uma vez por sessão e fica guardado nela,
 * para não custar uma consulta a cada tela. Trocar o tema em Ajustes atualiza a sessão atual; outras
 * sessões abertas (outro aparelho) passam a usar o tema novo quando a pessoa entrar de novo.
 *
 * <p>Não é cookie de propósito: o "Sair" pede ao navegador para apagar os cookies do site
 * (Clear-Site-Data), e a página de Privacidade promete um cookie só, o da sessão.
 */
@Component
public class ThemeSession {

    /** O id do usuário vai no nome, para uma sessão que troque de conta não herdar o tema da anterior. */
    private static final String ATTRIBUTE = "financas.theme.";

    private final UserSettingsRepository settings;

    public ThemeSession(UserSettingsRepository settings) {
        this.settings = settings;
    }

    public Theme current(Long userId, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(ATTRIBUTE + userId) instanceof Theme cached) {
            return cached;
        }
        Theme theme = settings.findThemeByUserId(userId).orElse(Theme.SYSTEM);
        if (session != null) {
            session.setAttribute(ATTRIBUTE + userId, theme);
        }
        return theme;
    }

    public void remember(Long userId, Theme theme, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(ATTRIBUTE + userId, theme);
        }
    }
}
