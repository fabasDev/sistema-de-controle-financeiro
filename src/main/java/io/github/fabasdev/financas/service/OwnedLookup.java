package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import org.springframework.stereotype.Component;

/**
 * Busca categoria e meta escolhidas num formulário conferindo o dono. Sem isso, alguém poderia
 * mandar o id da categoria de outra pessoa e ligar o próprio lançamento a ela.
 */
@Component
public class OwnedLookup {

    private final CategoryRepository categories;
    private final GoalRepository goals;

    public OwnedLookup(CategoryRepository categories, GoalRepository goals) {
        this.categories = categories;
        this.goals = goals;
    }

    /** null quando não foi escolhida. Id de outro usuário dá erro no campo, como se não existisse. */
    public Category category(Long userId, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new InvalidInputException("categoryId", "Escolha uma categoria da lista."));
    }

    public Goal goal(Long userId, Long goalId) {
        if (goalId == null) {
            return null;
        }
        return goals.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new InvalidInputException("goalId", "Escolha uma meta da lista."));
    }
}
