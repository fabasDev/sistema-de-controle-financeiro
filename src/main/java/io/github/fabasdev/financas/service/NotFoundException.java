package io.github.fabasdev.financas.service;

/**
 * Registro inexistente ou de outro usuário. Os dois casos dão a mesma resposta (404),
 * para não revelar que o id existe para outra pessoa.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Registro não encontrado");
    }
}
