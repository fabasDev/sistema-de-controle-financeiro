package io.github.fabasdev.financas.controller;

import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Tratamento de erros comum a todas as telas. O usuário vê só uma mensagem genérica
 * (templates/error.html), sem stack trace nem detalhe interno.
 */
@ControllerAdvice
public class GlobalControllerAdvice {

    /** Nome do usuário logado, para o cabeçalho das telas. */
    @ModelAttribute("currentUsername")
    public String currentUsername(@AuthenticationPrincipal AppUserPrincipal principal) {
        return principal == null ? null : principal.getUsername();
    }

    /** Caminho da página (sem o contexto), para o menu marcar onde o usuário está. Só é comparado, nunca exibido. */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** Registro inexistente ou de outro usuário: mesma resposta 404 nos dois casos. */
    @ExceptionHandler({NotFoundException.class, MethodArgumentTypeMismatchException.class})
    public String notFound(HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("status", 404);
        return "error";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        model.addAttribute("status", 413);
        return "error";
    }

    // Os outros erros seguem para o tratamento padrão do Spring Boot: o status HTTP certo
    // (400, 405, 500...), o registro no log do servidor e a mesma página genérica error.html.
}
