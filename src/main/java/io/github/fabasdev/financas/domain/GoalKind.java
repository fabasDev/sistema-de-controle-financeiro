package io.github.fabasdev.financas.domain;

public enum GoalKind {
    /** Reserva de emergência: o valor-alvo é calculado pelo custo de vida. */
    RESERVE("Reserva de emergência"),
    INVESTMENT("Investimentos"),
    GOAL("Meta");

    private final String label;

    GoalKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
