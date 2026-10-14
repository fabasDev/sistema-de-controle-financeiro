package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.controller.view.MonthParam;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.InvestmentSuggestionService;
import io.github.fabasdev.financas.service.MonthSummaryService;
import io.github.fabasdev.financas.service.RecurringService;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Visão geral do mês: quanto gastei, quanto sobra e quanto posso investir. */
@Controller
public class HomeController {

    private final MonthSummaryService summaries;
    private final InvestmentSuggestionService suggestions;
    private final RecurringService recurring;
    private final Clock clock;

    public HomeController(MonthSummaryService summaries, InvestmentSuggestionService suggestions,
            RecurringService recurring, Clock clock) {
        this.summaries = summaries;
        this.suggestions = suggestions;
        this.recurring = recurring;
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
        model.addAttribute("summary", summaries.summarize(me.getId(), month));
        model.addAttribute("rec", suggestions.recommendation(me.getId(), month));
        return "home";
    }
}
