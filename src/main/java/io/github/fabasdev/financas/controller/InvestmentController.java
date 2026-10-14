package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.domain.AssetClass;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.domain.RiskProfile;
import io.github.fabasdev.financas.dto.Recommendation;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.BusinessRuleException;
import io.github.fabasdev.financas.service.InvalidInputException;
import io.github.fabasdev.financas.service.InvestmentSuggestionService;
import io.github.fabasdev.financas.service.ProfileService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Perfil de investidor e carteira sugerida do mês. */
@Controller
@RequestMapping("/investimentos")
public class InvestmentController {

    private final ProfileService profiles;
    private final InvestmentSuggestionService suggestions;
    private final Clock clock;

    public InvestmentController(ProfileService profiles, InvestmentSuggestionService suggestions, Clock clock) {
        this.profiles = profiles;
        this.suggestions = suggestions;
        this.clock = clock;
    }

    @GetMapping
    public String overview(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        Recommendation rec = suggestions.recommendation(me.getId(), YearMonth.now(clock));
        model.addAttribute("rec", rec);
        profiles.find(me.getId()).ifPresent(p -> {
            model.addAttribute("profile", p);
            model.addAttribute("plan", profiles.plan(p, rec.toInvest()));
        });
        model.addAttribute("profiles", RiskProfile.values());
        model.addAttribute("assetClasses", AssetClass.values());
        model.addAttribute("maxScore", ProfileService.MAX_SCORE);
        return "investments/overview";
    }

    /**
     * Resultado do questionário. Na Fase 3 o questionário em JS manda o perfil e a pontuação;
     * por enquanto dá para escolher o perfil direto na tela.
     */
    @PostMapping("/perfil")
    public String saveProfile(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam("profile") RiskProfile profile, @RequestParam(name = "score", defaultValue = "0") int score,
            RedirectAttributes redirect) {
        try {
            profiles.saveResult(me.getId(), profile, score);
            redirect.addFlashAttribute("success", "Perfil salvo.");
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/investimentos";
    }

    /** Percentuais por classe. Campo vazio volta ao padrão do perfil. */
    @PostMapping("/carteira")
    public String saveAllocation(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam Map<String, String> params, RedirectAttributes redirect) {
        Map<AssetClass, BigDecimal> custom = new EnumMap<>(AssetClass.class);
        for (AssetClass c : AssetClass.values()) {
            String raw = params.get("pct_" + c.name());
            if (raw == null || raw.isBlank()) {
                continue;
            }
            BigDecimal value = Money.parse(raw).orElse(null);
            if (value == null || value.signum() < 0 || value.compareTo(Money.HUNDRED) > 0) {
                redirect.addFlashAttribute("error", "Use números de 0 a 100 em cada classe.");
                return "redirect:/investimentos";
            }
            custom.put(c, value);
        }
        try {
            profiles.saveCustomAllocation(me.getId(), custom);
            redirect.addFlashAttribute("success", "Carteira ajustada.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/investimentos";
    }
}
