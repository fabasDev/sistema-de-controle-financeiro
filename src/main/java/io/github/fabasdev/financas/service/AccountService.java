package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.User;
import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.security.LoginAttemptService;
import io.github.fabasdev.financas.security.PasswordPolicy;
import io.github.fabasdev.financas.security.RecoveryCodes;
import io.github.fabasdev.financas.security.SecurityLog;
import io.github.fabasdev.financas.security.UserSessions;
import io.github.fabasdev.financas.security.UsernameRules;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro, recuperação, troca de senha, novo código de recuperação e exclusão de conta. */
@Service
public class AccountService {

    static final String RECOVER_SCOPE = "recover";
    static final String REAUTH_SCOPE = "reauth";
    static final String REGISTER_SCOPE = "register";
    static final int REGISTER_THRESHOLD = 20;
    /** Mensagem única para usuário inexistente e código errado (não revela quem tem conta). */
    static final String RECOVERY_FAILED = "Usuário ou código de recuperação incorretos.";
    static final String TOO_MANY = "Muitas tentativas. Espere alguns minutos e tente de novo.";
    public static final String DELETE_WORD = "EXCLUIR";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final UserDataProvisioner provisioner;
    private final UserDataEraser eraser;
    private final LoginAttemptService attempts;
    private final UserSessions sessions;
    private final Clock clock;
    /** Hash de um código qualquer, para gastar o mesmo tempo quando o usuário não existe. */
    private final String dummyHash;

    public AccountService(UserRepository users, PasswordEncoder encoder, PasswordPolicy policy,
            UserDataProvisioner provisioner, UserDataEraser eraser, LoginAttemptService attempts,
            UserSessions sessions, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.provisioner = provisioner;
        this.eraser = eraser;
        this.attempts = attempts;
        this.sessions = sessions;
        this.clock = clock;
        this.dummyHash = encoder.encode(RecoveryCodes.normalize(RecoveryCodes.generate()));
    }

    /**
     * Cria a conta com os dados padrão e devolve o código de recuperação, que é mostrado uma vez só.
     * Pela decisão da Fase 1, o cadastro avisa quando o usuário já existe.
     */
    @Transactional
    public String register(String rawUsername, String password, String confirm, String ip) {
        if (attempts.blockedForIp(REGISTER_SCOPE, ip).isPresent()) {
            SecurityLog.warn("cadastro_bloqueado", "", ip);
            throw new InvalidInputException("username", TOO_MANY);
        }
        String username = UsernameRules.normalize(rawUsername);
        UsernameRules.validate(username).ifPresent(msg -> {
            throw new InvalidInputException("username", msg);
        });
        checkNewPassword(password, confirm, username);
        // Conta cada tentativa de cadastro por IP (inclusive as de usuário repetido, que serviriam
        // para descobrir quem tem conta). A partir de 20 em uma hora, começa a espera.
        attempts.recordForIp(REGISTER_SCOPE, ip, REGISTER_THRESHOLD);
        if (users.existsByUsername(username)) {
            throw new InvalidInputException("username", "Esse usuário já existe. Escolha outro ou entre na sua conta.");
        }
        User user;
        try {
            user = users.saveAndFlush(new User(username, encoder.encode(password), Instant.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // Outra pessoa criou o mesmo usuário no mesmo instante.
            throw new InvalidInputException("username", "Esse usuário já existe. Escolha outro ou entre na sua conta.");
        }
        String code = RecoveryCodes.generate();
        user.setRecoveryHash(encoder.encode(RecoveryCodes.normalize(code)));
        provisioner.provision(user);
        SecurityLog.event("cadastro", username, ip);
        return code;
    }

    /**
     * Troca a senha usando o código de recuperação. O código usado deixa de valer, e um novo
     * é devolvido. Todas as sessões abertas da conta são encerradas.
     */
    @Transactional(noRollbackFor = InvalidInputException.class)
    public String recover(String rawUsername, String code, String newPassword, String confirm, String ip) {
        String username = UsernameRules.normalize(rawUsername);
        failIfBlocked(RECOVER_SCOPE, username, ip, "code");
        checkNewPassword(newPassword, confirm, username);

        String normalizedCode = RecoveryCodes.normalize(code);
        Optional<User> found = username.length() > UsernameRules.MAX ? Optional.empty() : users.findByUsername(username);
        boolean valid = found.map(u -> u.getRecoveryHash() != null && encoder.matches(normalizedCode, u.getRecoveryHash()))
                .orElseGet(() -> {
                    encoder.matches(normalizedCode, dummyHash);
                    return false;
                });
        if (!valid) {
            attempts.recordFailure(RECOVER_SCOPE, username, ip);
            SecurityLog.warn("recuperacao_falhou", username, ip);
            throw new InvalidInputException("code", RECOVERY_FAILED);
        }
        User user = found.orElseThrow();
        attempts.recordSuccess(RECOVER_SCOPE, username, ip);
        user.setPasswordHash(encoder.encode(newPassword));
        String newCode = RecoveryCodes.generate();
        user.setRecoveryHash(encoder.encode(RecoveryCodes.normalize(newCode)));
        sessions.expireAll(user.getId(), null);
        SecurityLog.event("recuperacao_ok", username, ip);
        return newCode;
    }

    /** Troca a senha de quem está logado. Pede a senha atual e encerra as outras sessões. */
    @Transactional(noRollbackFor = InvalidInputException.class)
    public void changePassword(Long userId, String current, String newPassword, String confirm, String ip,
            String currentSessionId) {
        User user = requireCurrentPassword(userId, current, ip);
        checkNewPassword(newPassword, confirm, user.getUsername());
        user.setPasswordHash(encoder.encode(newPassword));
        sessions.expireAll(userId, currentSessionId);
        SecurityLog.event("senha_trocada", user.getUsername(), ip);
    }

    /** Gera um novo código de recuperação (o anterior deixa de valer). Pede a senha atual. */
    @Transactional(noRollbackFor = InvalidInputException.class)
    public String newRecoveryCode(Long userId, String current, String ip) {
        User user = requireCurrentPassword(userId, current, ip);
        String code = RecoveryCodes.generate();
        user.setRecoveryHash(encoder.encode(RecoveryCodes.normalize(code)));
        SecurityLog.event("novo_codigo_recuperacao", user.getUsername(), ip);
        return code;
    }

    /** Exclui a conta e todos os dados. Pede a senha e a palavra EXCLUIR. */
    @Transactional(noRollbackFor = InvalidInputException.class)
    public void deleteAccount(Long userId, String current, String typedWord, String ip) {
        if (!DELETE_WORD.equals(typedWord == null ? "" : typedWord.strip())) {
            throw new InvalidInputException("confirmWord", "Digite " + DELETE_WORD + " para confirmar.");
        }
        User user = requireCurrentPassword(userId, current, ip);
        String username = user.getUsername();
        eraser.deleteAccount(userId);
        sessions.expireAll(userId, null);
        SecurityLog.event("conta_excluida", username, ip);
    }

    private User requireCurrentPassword(Long userId, String current, String ip) {
        User user = users.findById(userId).orElseThrow(NotFoundException::new);
        failIfBlocked(REAUTH_SCOPE, user.getUsername(), ip, "currentPassword");
        if (current == null || PasswordPolicy.exceedsMaxBytes(current) || !encoder.matches(current, user.getPasswordHash())) {
            attempts.recordFailure(REAUTH_SCOPE, user.getUsername(), ip);
            SecurityLog.warn("senha_atual_errada", user.getUsername(), ip);
            throw new InvalidInputException("currentPassword", "Senha atual incorreta.");
        }
        attempts.recordSuccess(REAUTH_SCOPE, user.getUsername(), ip);
        return user;
    }

    private void checkNewPassword(String password, String confirm, String username) {
        policy.check(password, username).ifPresent(msg -> {
            throw new InvalidInputException("password", msg);
        });
        if (!password.equals(confirm)) {
            throw new InvalidInputException("confirm", "As senhas não são iguais.");
        }
    }

    private void failIfBlocked(String scope, String username, String ip, String field) {
        Optional<Duration> wait = attempts.blockedFor(scope, username, ip);
        if (wait.isPresent()) {
            SecurityLog.warn(scope + "_bloqueado", username, ip);
            throw new InvalidInputException(field, TOO_MANY);
        }
    }
}
