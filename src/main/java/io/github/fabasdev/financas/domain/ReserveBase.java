package io.github.fabasdev.financas.domain;

/** O que entra no custo mensal usado para calcular o tamanho da reserva. */
public enum ReserveBase {
    /** Só gastos essenciais (categorias de necessidade). */
    NEEDS("gastos essenciais"),
    /** Todos os gastos, menos o que foi guardado. */
    SPENDING("gastos totais"),
    /** Valor digitado pelo usuário. */
    MANUAL("valor definido por você");

    private final String label;

    ReserveBase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
