package io.github.fabasdev.financas.domain;

/** Natureza de um pote ou categoria. Decide o que é consumo e o que é dinheiro guardado. */
public enum BucketKind {
    NEEDS("Necessidade"),
    WANTS("Desejo"),
    /** Guardar ou investir: despesas nestas categorias não contam como consumo. */
    FUTURE("Futuro (guardar/investir)"),
    OTHER("Outro");

    private final String label;

    BucketKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
