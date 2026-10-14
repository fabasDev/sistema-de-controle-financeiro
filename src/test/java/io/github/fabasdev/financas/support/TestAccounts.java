package io.github.fabasdev.financas.support;

import io.github.fabasdev.financas.domain.User;
import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.AccountService;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Cria contas de verdade (pelo AccountService) para os testes de segurança e autorização. */
@Component
public class TestAccounts {

    /** Senha que passa na política: longa, fora da lista de comuns e sem o nome do usuário. */
    public static final String PASSWORD = "tamandua azul pula a cerca";

    public record Account(User user, String password, String recoveryCode) {
        public AppUserPrincipal principal() {
            return new AppUserPrincipal(user.getId(), user.getUsername(), user.getPasswordHash());
        }
    }

    private final AccountService accounts;
    private final UserRepository users;

    public TestAccounts(AccountService accounts, UserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }

    public Account create() {
        String username = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String code = accounts.register(username, PASSWORD, PASSWORD, randomIp());
        return new Account(users.findByUsername(username).orElseThrow(), PASSWORD, code);
    }

    /**
     * Cada teste usa um IP diferente, para os limites de tentativa por IP de um teste
     * não interferirem nos outros.
     */
    public static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + (1 + r.nextInt(254));
    }

    public static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
