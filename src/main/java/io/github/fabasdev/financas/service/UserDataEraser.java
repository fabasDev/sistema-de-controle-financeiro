package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.InvestorProfileRepository;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apaga dados de um usuário na ordem certa das chaves estrangeiras
 * (lançamentos, contas fixas, categorias, metas, potes). Igual no H2 e no PostgreSQL.
 */
@Service
public class UserDataEraser {

    private final TransactionRepository transactions;
    private final RecurringEntryRepository recurring;
    private final CategoryRepository categories;
    private final GoalRepository goals;
    private final BucketRepository buckets;
    private final InvestorProfileRepository profiles;
    private final UserSettingsRepository settings;
    private final UserRepository users;

    public UserDataEraser(TransactionRepository transactions, RecurringEntryRepository recurring,
            CategoryRepository categories, GoalRepository goals, BucketRepository buckets,
            InvestorProfileRepository profiles, UserSettingsRepository settings, UserRepository users) {
        this.transactions = transactions;
        this.recurring = recurring;
        this.categories = categories;
        this.goals = goals;
        this.buckets = buckets;
        this.profiles = profiles;
        this.settings = settings;
        this.users = users;
    }

    /** Apaga os dados financeiros, mas mantém a conta e as configurações. */
    @Transactional
    public void eraseFinancialData(Long userId) {
        transactions.deleteAllByUserId(userId);
        recurring.deleteAllByUserId(userId);
        categories.deleteAllByUserId(userId);
        goals.deleteAllByUserId(userId);
        buckets.deleteAllByUserId(userId);
        profiles.deleteAllByUserId(userId);
    }

    /** Exclui a conta e tudo o que pertence a ela. */
    @Transactional
    public void deleteAccount(Long userId) {
        eraseFinancialData(userId);
        settings.deleteAllByUserId(userId);
        users.deleteById(userId);
    }
}
