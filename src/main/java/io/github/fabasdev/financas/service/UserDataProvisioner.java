package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.User;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cria os dados iniciais de uma conta nova: configurações, potes 50/30/20, categorias e metas padrão. */
@Service
public class UserDataProvisioner {

    private final RuleService rules;
    private final CategoryRepository categories;
    private final GoalRepository goals;
    private final UserSettingsRepository settings;

    public UserDataProvisioner(RuleService rules, CategoryRepository categories, GoalRepository goals,
            UserSettingsRepository settings) {
        this.rules = rules;
        this.categories = categories;
        this.goals = goals;
        this.settings = settings;
    }

    @Transactional
    public void provision(User user) {
        settings.save(new UserSettings(user, DefaultData.DEFAULT_PRESET));

        Map<String, Goal> goalsByKey = new HashMap<>();
        for (DefaultData.DefaultGoal g : DefaultData.GOALS) {
            goalsByKey.put(g.key(), goals.save(new Goal(user, g.key(), g.name(), g.icon(), g.kind())));
        }

        List<RuleService.BucketWithClaims> buckets = rules.createBucketsFromPreset(user, DefaultData.DEFAULT_PRESET);
        for (DefaultData.DefaultCategory c : DefaultData.CATEGORIES) {
            Category category = new Category(user, c.key(), c.name(), c.icon(),
                    RuleService.bucketFor(c.key(), c.kind(), buckets), c.kind());
            if (c.goalKey() != null) {
                category.setGoal(goalsByKey.get(c.goalKey()));
            }
            categories.save(category);
        }
    }
}
