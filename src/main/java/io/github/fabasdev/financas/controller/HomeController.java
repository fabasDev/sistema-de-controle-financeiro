package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.controller.view.MonthParam;
import io.github.fabasdev.financas.dto.MonthSummary;
import io.github.fabasdev.financas.dto.TransactionRow;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.CategoryService;
import io.github.fabasdev.financas.service.InvestmentSuggestionService;
import io.github.fabasdev.financas.service.MonthSummaryService;
import io.github.fabasdev.financas.service.RecurringService;
import io.github.fabasdev.financas.service.SettingsService;
import io.github.fabasdev.financas.service.TransactionService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Visão geral do mês: quanto gastei, quanto sobra e quanto posso investir. */
@Controller
public class HomeController {

    /** Quantas pendências aparecem na visão geral; as outras ficam em Lançamentos. */
    static final int PENDING_SHOWN = 8;

    private final MonthSummaryService summaries;
    private final InvestmentSuggestionService suggestions;
    private final RecurringService recurring;
    private final CategoryService categories;
    private final SettingsService settings;
    private final TransactionService transactions;
    private final Clock clock;

    public HomeController(MonthSummaryService summaries, InvestmentSuggestionService suggestions,
            RecurringService recurring, CategoryService categories, SettingsService settings,
            TransactionService transactions, Clock clock) {
        this.summaries = summaries;
        this.suggestions = suggestions;
        this.recurring = recurring;
        this.categories = categories;
        this.settings = settings;
        this.transactions = transactions;
        this.clock = clock;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "mes", required = false) String monthParam, Model model) {
        YearMonth month = MonthParam.parse(monthParam, clock);
        recurring.ensureMonth(me.getId(), month);
        model.addAttribute("month", month);
        model.addAttribute("previousMonth", month.minusMonths(1));
        model.addAttribute("nextMonth", month.plusMonths(1));
        MonthSummary summary = summaries.summarize(me.getId(), month);
        model.addAttribute("summary", summary);
        model.addAttribute("rec", suggestions.recommendation(me.getId(), month, summary));
        // Uma a mais do que aparece: se ela vier, há mais pendências e a tela mostra o link para todas.
        List<TransactionRow> pending = transactions.pending(me.getId(), month, PENDING_SHOWN + 1);
        model.addAttribute("pending", pending.subList(0, Math.min(pending.size(), PENDING_SHOWN)));
        model.addAttribute("pendingMore", pending.size() > PENDING_SHOWN);
        model.addAttribute("quick", categories.quickAddOptions(me.getId(), LocalDate.now(clock)));
        model.addAttribute("onboarded", settings.get(me.getId()).isOnboarded());
        model.addAttribute("today", LocalDate.now(clock));
        return "home";
    }
}
