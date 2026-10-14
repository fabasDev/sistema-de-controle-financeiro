package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.controller.view.MonthParam;
import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.dto.MonthSummary;
import io.github.fabasdev.financas.dto.PlanItem;
import io.github.fabasdev.financas.dto.Recommendation;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.GoalService;
import io.github.fabasdev.financas.service.InvestmentSuggestionService;
import io.github.fabasdev.financas.service.MonthSummaryService;
import io.github.fabasdev.financas.service.ProfileService;
import io.github.fabasdev.financas.service.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Números que o assistente (em JS, Fase 3) usa para montar as respostas. As contas de dinheiro
 * já chegam prontas daqui; o navegador só escolhe o texto. As perguntas não são enviadas ao servidor.
 */
@RestController
public class AdvisorApiController {

    public record CategoryAmount(String name, BucketKind kind, BigDecimal amount) {
    }

    /** @param spentShare gasto / renda (0,4 = 40%), já calculado aqui para o navegador só exibir */
    public record AdvisorContext(YearMonth month, String displayName, int reserveMonths, Recommendation recommendation,
            BigDecimal income, BigDecimal spent, BigDecimal spentShare, List<CategoryAmount> topCategories,
            List<String> bucketsOverPlan,
            String profile, Integer profileScore, boolean profileExpired, List<PlanItem> plan, int goalCount) {
    }

    private final MonthSummaryService summaries;
    private final InvestmentSuggestionService suggestions;
    private final ProfileService profiles;
    private final SettingsService settings;
    private final GoalService goals;
    private final Clock clock;

    public AdvisorApiController(MonthSummaryService summaries, InvestmentSuggestionService suggestions,
            ProfileService profiles, SettingsService settings, GoalService goals, Clock clock) {
        this.summaries = summaries;
        this.suggestions = suggestions;
        this.profiles = profiles;
        this.settings = settings;
        this.goals = goals;
        this.clock = clock;
    }

    @GetMapping("/api/assistente")
    public AdvisorContext context(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "mes", required = false) String monthParam) {
        YearMonth month = MonthParam.parse(monthParam, clock);
        MonthSummary summary = summaries.summarize(me.getId(), month);
        Recommendation rec = suggestions.recommendation(me.getId(), month, summary);
        var s = settings.get(me.getId());
        var profile = profiles.find(me.getId());
        List<CategoryAmount> top = summary.categories().stream()
                .filter(c -> c.kind() != BucketKind.FUTURE)
                .limit(3)
                .map(c -> new CategoryAmount(c.name(), c.kind(), c.actual()))
                .toList();
        List<String> over = summary.buckets().stream()
                .filter(b -> b.kind() != BucketKind.FUTURE && b.actual().compareTo(b.planned()) > 0)
                .map(MonthSummary.BucketSummary::name)
                .toList();
        long goalCount = goals.list(me.getId(), month).stream()
                .filter(g -> g.kind() == io.github.fabasdev.financas.domain.GoalKind.GOAL).count();
        return new AdvisorContext(month, s.getDisplayName(), s.getReserveMonths(), rec, summary.income(),
                summary.spent(), Money.ratio(summary.spent(), summary.income()), top, over,
                profile.map(p -> p.profile().name()).orElse(null),
                profile.map(ProfileService.ProfileView::score).orElse(null),
                profile.map(ProfileService.ProfileView::expired).orElse(false),
                profile.map(p -> profiles.plan(p, rec.toInvest())).orElse(List.of()),
                (int) goalCount);
    }
}
