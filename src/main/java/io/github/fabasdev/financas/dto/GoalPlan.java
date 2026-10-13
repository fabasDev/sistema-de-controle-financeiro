package io.github.fabasdev.financas.dto;

import java.math.BigDecimal;

/**
 * Andamento de uma meta.
 *
 * @param monthsLeft    meses até o prazo, contando o atual (null sem prazo)
 * @param monthlyNeeded quanto guardar por mês para chegar no prazo (null sem prazo ou sem alvo)
 */
public record GoalPlan(
        BigDecimal balance,
        BigDecimal remaining,
        BigDecimal progress,
        Integer monthsLeft,
        BigDecimal monthlyNeeded,
        boolean done) {
}
