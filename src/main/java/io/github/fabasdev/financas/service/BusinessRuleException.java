package io.github.fabasdev.financas.service;

/** Pedido válido no formato, mas que uma regra do app não permite. A mensagem é para o usuário. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String userMessage) {
        super(userMessage);
    }
}
