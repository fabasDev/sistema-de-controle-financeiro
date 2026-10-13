package io.github.fabasdev.financas.dto;

import java.math.BigDecimal;

/**
 * Situação da reserva de emergência.
 *
 * @param target        custo mensal × meses desejados
 * @param gap           quanto falta para o alvo (nunca negativo)
 * @param progress      saldo / alvo, limitado a 1
 * @param monthsCovered quantos meses de custo a reserva atual cobre
 * @param basis         de onde veio o custo mensal, em texto para a tela
 */
public record ReserveInfo(
        BigDecimal monthlyCost,
        BigDecimal target,
        BigDecimal balance,
        BigDecimal gap,
        BigDecimal progress,
        BigDecimal monthsCovered,
        String basis) {
}
