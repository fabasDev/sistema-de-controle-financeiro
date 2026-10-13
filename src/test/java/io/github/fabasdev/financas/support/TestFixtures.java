package io.github.fabasdev.financas.support;

import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.domain.User;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.service.UserDataProvisioner;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Cria usuários com os dados padrão e lançamentos para os testes de integração. */
@Component
public class TestFixtures {

    private final UserRepository users;
    private final UserDataProvisioner provisioner;
    private final CategoryRepository categories;
    private final GoalRepository goals;
    private final TransactionRepository transactions;

    public TestFixtures(UserRepository users, UserDataProvisioner provisioner, CategoryRepository categories,
            GoalRepository goals, TransactionRepository transactions) {
        this.users = users;
        this.provisioner = provisioner;
        this.categories = categories;
        this.goals = goals;
        this.transactions = transactions;
    }

    /** Usuário novo, já com as categorias, potes 50/30/20 e metas padrão (como createInitialData). */
    public User newUser() {
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        User user = users.save(new User(username, "{noop}nao-usada-nos-testes", Instant.now()));
        provisioner.provision(user);
        return user;
    }

    public Category category(User user, String key) {
        return categories.findByUserIdAndSystemKey(user.getId(), key).orElseThrow();
    }

    public Goal goal(User user, String key) {
        return goals.findByUserIdAndSystemKey(user.getId(), key).orElseThrow();
    }

    public Transaction income(User user, String amount, LocalDate date) {
        return transactions.save(
                new Transaction(user, TransactionType.INCOME, date, "Receita", new BigDecimal(amount), true));
    }

    public Transaction expense(User user, String categoryKey, String amount, LocalDate date) {
        return expense(user, categoryKey, amount, date, true, null);
    }

    public Transaction expense(User user, String categoryKey, String amount, LocalDate date, boolean paid,
            Goal goal) {
        Transaction t = new Transaction(user, TransactionType.EXPENSE, date, "Despesa", new BigDecimal(amount), paid);
        if (categoryKey != null) {
            t.setCategory(category(user, categoryKey));
        }
        t.setGoal(goal);
        return transactions.save(t);
    }
}
