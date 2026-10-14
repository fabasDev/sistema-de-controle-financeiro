package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.domain.Months;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.HistoryService;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Entradas × saídas e gastos por categoria nos últimos 6, 12 ou 24 meses. */
@Controller
public class HistoryController {

    private final HistoryService history;
    private final Clock clock;

    public HistoryController(HistoryService history, Clock clock) {
        this.history = history;
        this.clock = clock;
    }

    @GetMapping("/historico")
    public String page(@AuthenticationPrincipal AppUserPrincipal me,
            @RequestParam(name = "meses", defaultValue = "6") int months, Model model) {
        int count = months == 12 || months == 24 ? months : 6;
        YearMonth end = YearMonth.now(clock);
        model.addAttribute("count", count);
        model.addAttribute("months", Months.range(end, count));
        var rows = history.months(me.getId(), end, count);
        model.addAttribute("rows", rows);
        model.addAttribute("hasData", rows.stream().anyMatch(r -> r.income().signum() != 0
                || r.spent().signum() != 0 || r.saved().signum() != 0));
        model.addAttribute("categories", history.categories(me.getId(), end, count));
        return "history/page";
    }
}
