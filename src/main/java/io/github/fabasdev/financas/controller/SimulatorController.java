package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.dto.Simulation;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.SettingsService;
import io.github.fabasdev.financas.service.SimulationService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Simulador de juros compostos. A conta é sempre feita no servidor (BigDecimal).
 * A página funciona sem JavaScript, e a API /api/simulacao serve o gráfico da Fase 3.
 */
@Controller
public class SimulatorController {

    /**
     * Resultado completo de uma simulação. Os totais finais e o ganho com juros já vêm calculados
     * (BigDecimal), para o JavaScript só exibir.
     */
    public record SimulationResult(List<Simulation.Point> points, BigDecimal finalTotal, BigDecimal finalInvested,
            BigDecimal finalReal, BigDecimal interestEarned, Integer monthsToTarget, BigDecimal requiredMonthly) {
    }

    private final SimulationService simulator;
    private final SettingsService settings;

    public SimulatorController(SimulationService simulator, SettingsService settings) {
        this.simulator = simulator;
        this.settings = settings;
    }

    /** Os campos chegam como texto pt-BR ("10,5"). Sem campos, usa os últimos valores salvos. */
    @GetMapping("/simulador")
    public String page(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam Map<String, String> params,
            Model model) {
        UserSettings s = settings.get(me.getId());
        List<String> errors = new ArrayList<>();
        BigDecimal initial = field(params, "inicial", s.getSimInitial(), Money.MAX, errors, "Valor inicial");
        BigDecimal monthly = field(params, "mensal", s.getSimMonthly(), Money.MAX, errors, "Aporte mensal");
        BigDecimal rate = field(params, "taxa", s.getSimAnnualRate(), Money.HUNDRED, errors, "Rentabilidade");
        BigDecimal inflation = field(params, "inflacao", s.getSimInflation(), Money.HUNDRED, errors, "Inflação");
        BigDecimal increase = field(params, "aumento", s.getSimYearlyIncrease(), Money.HUNDRED, errors, "Aumento do aporte");
        BigDecimal target = field(params, "meta", s.getSimTarget(), Money.MAX, errors, "Meta");
        BigDecimal yearsValue = field(params, "anos", BigDecimal.valueOf(s.getSimYears()), BigDecimal.valueOf(50), errors,
                "Anos");
        int years = yearsValue == null ? s.getSimYears() : Math.max(1, yearsValue.intValue());

        model.addAttribute("values", Map.of("inicial", show(initial), "mensal", show(monthly), "taxa", show(rate),
                "inflacao", show(inflation), "aumento", show(increase), "meta", show(target), "anos", String.valueOf(years)));
        model.addAttribute("errors", errors);
        if (errors.isEmpty()) {
            Simulation.Params p = new Simulation.Params(initial, monthly, rate, years, inflation, increase);
            SimulationResult result = run(p, target);
            model.addAttribute("result", result);
            model.addAttribute("last", result.points().getLast());
            // Na tabela, um ponto por ano.
            model.addAttribute("yearly", result.points().stream().filter(pt -> pt.month() % 12 == 0 && pt.month() > 0).toList());
        }
        return "simulator/page";
    }

    @PostMapping("/simulador/salvar")
    public String save(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam Map<String, String> params,
            RedirectAttributes redirect) {
        UserSettings s = settings.get(me.getId());
        List<String> errors = new ArrayList<>();
        BigDecimal initial = field(params, "inicial", s.getSimInitial(), Money.MAX, errors, "Valor inicial");
        BigDecimal monthly = field(params, "mensal", s.getSimMonthly(), Money.MAX, errors, "Aporte mensal");
        BigDecimal rate = field(params, "taxa", s.getSimAnnualRate(), Money.HUNDRED, errors, "Rentabilidade");
        BigDecimal inflation = field(params, "inflacao", s.getSimInflation(), Money.HUNDRED, errors, "Inflação");
        BigDecimal increase = field(params, "aumento", s.getSimYearlyIncrease(), Money.HUNDRED, errors, "Aumento do aporte");
        BigDecimal target = field(params, "meta", s.getSimTarget(), Money.MAX, errors, "Meta");
        BigDecimal yearsValue = field(params, "anos", BigDecimal.valueOf(s.getSimYears()), BigDecimal.valueOf(50), errors,
                "Anos");
        if (!errors.isEmpty()) {
            redirect.addFlashAttribute("error", String.join(" ", errors));
            return "redirect:/simulador";
        }
        int years = Math.max(1, yearsValue.intValue());
        settings.saveSimulation(me.getId(), new Simulation.Params(initial, monthly, rate, years, inflation, increase),
                target);
        redirect.addFlashAttribute("success", "Valores do simulador salvos.");
        return "redirect:/simulador";
    }

    /**
     * Mesma conta em JSON, para o JavaScript da Fase 3 desenhar o gráfico.
     * Os números chegam no formato do JS (ponto decimal). Fora dos limites, responde 400.
     */
    @GetMapping("/api/simulacao")
    @ResponseBody
    public ResponseEntity<SimulationResult> api(@RequestParam BigDecimal initial, @RequestParam BigDecimal monthly,
            @RequestParam BigDecimal annualRate, @RequestParam int years, @RequestParam BigDecimal inflation,
            @RequestParam BigDecimal yearlyIncrease, @RequestParam(defaultValue = "0") BigDecimal target) {
        boolean valid = within(initial, Money.MAX) && within(monthly, Money.MAX) && within(annualRate, Money.HUNDRED)
                && within(inflation, Money.HUNDRED) && within(yearlyIncrease, Money.HUNDRED) && within(target, Money.MAX)
                && years >= 1 && years <= 50;
        if (!valid) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(run(new Simulation.Params(Money.round(initial), Money.round(monthly), annualRate, years,
                inflation, yearlyIncrease), Money.round(target)));
    }

    private SimulationResult run(Simulation.Params p, BigDecimal target) {
        List<Simulation.Point> points = simulator.simulate(p);
        Integer months = null;
        BigDecimal required = null;
        if (target.signum() > 0) {
            Optional<Integer> m = simulator.monthsToTarget(p.initial(), p.monthly(), p.annualRate(), p.yearlyIncrease(),
                    target);
            months = m.orElse(null);
            required = simulator.requiredMonthly(p.initial(), p.annualRate(), p.years() * 12, target);
        }
        Simulation.Point last = points.getLast();
        return new SimulationResult(points, last.total(), last.invested(), last.real(),
                last.total().subtract(last.invested()), months, required);
    }

    private static boolean within(BigDecimal v, BigDecimal max) {
        return v.signum() >= 0 && v.compareTo(max) <= 0;
    }

    private static BigDecimal field(Map<String, String> params, String name, BigDecimal fallback, BigDecimal max,
            List<String> errors, String label) {
        String raw = params.get(name);
        if (raw == null) {
            return fallback;
        }
        Optional<BigDecimal> parsed = raw.length() > 20 ? Optional.empty() : Money.parse(raw);
        if (parsed.isEmpty() || parsed.get().signum() < 0 || parsed.get().compareTo(max) > 0) {
            errors.add(label + ": valor inválido.");
            return null;
        }
        return parsed.get();
    }

    private static String show(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
