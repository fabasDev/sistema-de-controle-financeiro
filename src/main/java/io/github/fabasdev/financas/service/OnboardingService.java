package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assistente inicial: regra, salário, contas fixas comuns e reserva, de uma vez. Porte de completeOnboarding. */
@Service
public class OnboardingService {

    /** Conta fixa sugerida: nome, chave da categoria padrão e dia. Porte de COMMON_BILLS (presets.ts). */
    public record CommonBill(String description, String categoryKey, int day) {
    }

    public static final List<CommonBill> COMMON_BILLS = List.of(
            new CommonBill("Aluguel / financiamento", "housing", 10),
            new CommonBill("Condomínio", "housing", 10),
            new CommonBill("Energia elétrica", "utilities", 15),
            new CommonBill("Água", "utilities", 15),
            new CommonBill("Internet", "utilities", 20),
            new CommonBill("Celular", "utilities", 20),
            new CommonBill("Plano de saúde", "health", 5),
            new CommonBill("Streaming", "subscriptions", 12));

    /**
     * @param billAmounts valor de cada conta de {@link #COMMON_BILLS}, na mesma ordem (null = não tem)
     */
    public record OnboardingData(String displayName, String presetId, BigDecimal income, int incomeDay,
            List<BigDecimal> billAmounts, BigDecimal reserveBalance, int reserveMonths) {
    }

    private final RuleService rules;
    private final RecurringService recurring;
    private final CategoryRepository categories;
    private final GoalRepository goals;
    private final UserSettingsRepository settings;
    private final Clock clock;

    public OnboardingService(RuleService rules, RecurringService recurring, CategoryRepository categories,
            GoalRepository goals, UserSettingsRepository settings, Clock clock) {
        this.rules = rules;
        this.recurring = recurring;
        this.categories = categories;
        this.goals = goals;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public void complete(Long userId, OnboardingData data) {
        YearMonth month = YearMonth.now(clock);
        UserSettings s = settings.findById(userId).orElseThrow(NotFoundException::new);
        if (!data.presetId().equals(s.getPresetId())) {
            rules.applyPreset(userId, data.presetId());
        }
        if (data.income() != null && data.income().signum() > 0) {
            recurring.create(userId, new RecurringService.RecurringData(TransactionType.INCOME, "Salário", data.income(),
                    null, null, data.incomeDay(), month, null, true));
        }
        for (int i = 0; i < COMMON_BILLS.size() && i < data.billAmounts().size(); i++) {
            BigDecimal amount = data.billAmounts().get(i);
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            CommonBill bill = COMMON_BILLS.get(i);
            Long categoryId = categories.findByUserIdAndSystemKey(userId, bill.categoryKey()).map(c -> c.getId())
                    .orElse(null);
            recurring.create(userId, new RecurringService.RecurringData(TransactionType.EXPENSE, bill.description(),
                    amount, categoryId, null, bill.day(), month, null, true));
        }
        if (data.reserveBalance() != null) {
            goals.findByUserIdAndSystemKey(userId, Goal.RESERVE_KEY)
                    .ifPresent(g -> g.setInitialBalance(data.reserveBalance()));
        }
        s.setDisplayName(data.displayName() == null ? "" : data.displayName().strip());
        s.setReserveMonths(data.reserveMonths());
        s.setOnboarded(true);
    }

    /** "Pular": não pergunta mais. */
    @Transactional
    public void skip(Long userId) {
        settings.findById(userId).orElseThrow(NotFoundException::new).setOnboarded(true);
    }
}
