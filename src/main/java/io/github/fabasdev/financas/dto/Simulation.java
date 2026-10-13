package io.github.fabasdev.financas.dto;

import java.math.BigDecimal;

/** Tipos do simulador de juros compostos. */
public final class Simulation {

    private Simulation() {
    }

    /**
     * @param annualRate     % ao ano
     * @param inflation      % ao ano
     * @param yearlyIncrease % de aumento do aporte a cada 12 meses
     */
    public record Params(BigDecimal initial, BigDecimal monthly, BigDecimal annualRate, int years,
            BigDecimal inflation, BigDecimal yearlyIncrease) {
    }

    /** @param real total em valores de hoje (descontada a inflação) */
    public record Point(int month, BigDecimal invested, BigDecimal total, BigDecimal real) {
    }
}
