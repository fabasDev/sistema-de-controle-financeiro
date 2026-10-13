package io.github.fabasdev.financas.dto;

import java.math.BigDecimal;

/**
 * Sugestão educativa do mês: quanto separar e como dividir entre reserva e investimentos.
 *
 * @param futurePlanned   quanto a regra manda separar para o futuro neste mês
 * @param savedThisMonth  quanto já foi guardado neste mês
 * @param monthsToReserve meses até completar a reserva no ritmo sugerido (null se não se aplica)
 */
public record Recommendation(
        BigDecimal income,
        BigDecimal futurePlanned,
        BigDecimal toReserve,
        BigDecimal toInvest,
        BigDecimal savedThisMonth,
        BigDecimal remainingToSave,
        Integer monthsToReserve,
        ReserveInfo reserve) {
}
