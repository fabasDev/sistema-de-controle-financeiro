package io.github.fabasdev.financas.service;

/** Erro de um campo do formulário. O controller mostra a mensagem ao lado do campo. */
public class InvalidInputException extends RuntimeException {

    private final String field;

    public InvalidInputException(String field, String userMessage) {
        super(userMessage);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
