package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.GoalKind;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.domain.ReserveBase;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.dto.GoalPlan;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Metas e configuração da reserva. Porte de addGoal, updateGoal, removeGoal (store.ts) e goalPlan. */
@Service
public class GoalService {

    public record GoalData(String name, String icon, GoalKind kind, BigDecimal target, YearMonth deadline,
            BigDecimal initialBalance) {
    }

    public record ReserveSettings(int months, ReserveBase base, BigDecimal manualCost, BigDecimal priority) {
    }

    /** Meta com o andamento calculado, pronta para a tela. */
    public record GoalView(Long id, String name, String icon, GoalKind kind, BigDecimal target, YearMonth deadline,
            boolean reserve, GoalPlan plan) {
    }

    private final GoalRepository goals;
    private final TransactionRepository transactions;
    private final UserSettingsRepository settings;
    private final UserRepository users;

    public GoalService(GoalRepository goals, TransactionRepository transactions, UserSettingsRepository settings,
            UserRepository users) {
        this.goals = goals;
        this.transactions = transactions;
        this.settings = settings;
        this.users = users;
    }

    /** Todas as metas com saldo e plano. Os saldos vêm de uma só consulta agrupada. */
    @Transactional(readOnly = true)
    public List<GoalView> list(Long userId, YearMonth month) {
        Map<Long, BigDecimal> paid = new HashMap<>();
        for (Object[] row : transactions.sumPaidExpensesByGoal(userId)) {
            paid.put((Long) row[0], (BigDecimal) row[1]);
        }
        return goals.findByUserIdOrderByIdAsc(userId).stream().map(g -> {
            BigDecimal balance = Money.round(g.getInitialBalance().add(paid.getOrDefault(g.getId(), Money.ZERO)));
            return new GoalView(g.getId(), g.getName(), g.getIcon(), g.getKind(), g.getTarget(), g.getDeadline(),
                    g.isReserve(), GoalPlanner.plan(g.getTarget(), g.getDeadline(), balance, month));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Goal> listEntities(Long userId) {
        return goals.findByUserIdOrderByIdAsc(userId);
    }

    @Transactional(readOnly = true)
    public Goal get(Long userId, Long id) {
        return goals.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
    }

    @Transactional
    public Goal create(Long userId, GoalData data) {
        if (data.kind() == GoalKind.RESERVE) {
            throw new InvalidInputException("kind", "A reserva de emergência já existe. Ajuste-a no topo da página.");
        }
        Goal g = new Goal(users.getReferenceById(userId), null, data.name(), data.icon(), data.kind());
        apply(g, data);
        return goals.save(g);
    }

    @Transactional
    public void update(Long userId, Long id, GoalData data) {
        Goal g = get(userId, id);
        if (g.isReserve() && data.kind() != GoalKind.RESERVE || !g.isReserve() && data.kind() == GoalKind.RESERVE) {
            throw new InvalidInputException("kind", "O tipo da reserva de emergência não muda.");
        }
        g.setName(data.name());
        g.setIcon(data.icon());
        g.setKind(data.kind());
        apply(g, data);
    }

    /** A reserva padrão não pode ser apagada. Lançamentos e categorias ligados à meta perdem a ligação. */
    @Transactional
    public void delete(Long userId, Long id) {
        Goal g = get(userId, id);
        if (g.isReserve()) {
            throw new BusinessRuleException("A reserva de emergência não pode ser apagada.");
        }
        // As chaves estrangeiras têm ON DELETE SET NULL: o banco solta os lançamentos,
        // contas fixas e categorias que apontavam para esta meta.
        goals.delete(g);
    }

    @Transactional(readOnly = true)
    public UserSettings settings(Long userId) {
        return settings.findById(userId).orElseThrow(NotFoundException::new);
    }

    @Transactional
    public void updateReserve(Long userId, ReserveSettings data) {
        UserSettings s = settings(userId);
        s.setReserveMonths(data.months());
        s.setReserveBase(data.base());
        s.setReserveManualCost(data.manualCost());
        s.setReservePriority(data.priority());
    }

    private static void apply(Goal g, GoalData data) {
        g.setTarget(data.target() == null ? Money.ZERO : data.target());
        g.setDeadline(data.deadline());
        g.setInitialBalance(data.initialBalance() == null ? Money.ZERO : data.initialBalance());
    }
}
