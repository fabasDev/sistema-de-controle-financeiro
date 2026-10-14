package io.github.fabasdev.financas.service.backup;

import io.github.fabasdev.financas.domain.AssetClass;
import io.github.fabasdev.financas.domain.Bucket;
import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.GoalKind;
import io.github.fabasdev.financas.domain.InvestorProfile;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.ReserveBase;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.domain.User;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.InvestorProfileRepository;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionExportRow;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import io.github.fabasdev.financas.security.PasswordPolicy;
import io.github.fabasdev.financas.security.SecurityLog;
import io.github.fabasdev.financas.service.DefaultData;
import io.github.fabasdev.financas.service.InvalidInputException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * "Exportar meus dados": monta um backup com tudo o que a conta tem, no formato que o app antigo
 * exportava (legacy-web/src/store/backup.ts), e o criptografa com uma senha escolhida na hora.
 * O mesmo arquivo volta pela tela de importação, aqui ou em outro servidor (portabilidade, LGPD art. 18, V).
 *
 * <p>Os ids do banco não saem como estão: viram "b12", "c34"... e as categorias e metas padrão saem
 * com o id do app antigo ("cat-outros", "goal-reserva"). É por esse id que a importação reconhece
 * os registros padrão, como a reserva de emergência, que não pode ser apagada.
 */
@Service
public class BackupExportService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final BucketRepository buckets;
    private final CategoryRepository categories;
    private final GoalRepository goals;
    private final RecurringEntryRepository recurring;
    private final TransactionRepository transactions;
    private final InvestorProfileRepository profiles;
    private final PasswordPolicy policy;
    private final Clock clock;
    private final TransactionTemplate readOnly;

    public BackupExportService(UserRepository users, UserSettingsRepository settings, BucketRepository buckets,
            CategoryRepository categories, GoalRepository goals, RecurringEntryRepository recurring,
            TransactionRepository transactions, InvestorProfileRepository profiles, PasswordPolicy policy,
            Clock clock, PlatformTransactionManager txManager) {
        this.users = users;
        this.settings = settings;
        this.buckets = buckets;
        this.categories = categories;
        this.goals = goals;
        this.recurring = recurring;
        this.transactions = transactions;
        this.profiles = profiles;
        this.policy = policy;
        this.clock = clock;
        this.readOnly = new TransactionTemplate(txManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * Confere a senha do arquivo e devolve o backup criptografado. A senha segue a mesma regra da
     * conta: quem pegar o arquivo pode tentar adivinhá-la no próprio computador, sem limite.
     */
    public byte[] export(Long userId, String password, String confirm, String ip) {
        String username = users.findById(userId).map(User::getUsername).orElseThrow();
        Optional<String> problem = policy.check(password, username);
        if (problem.isPresent()) {
            throw new InvalidInputException("password", problem.get());
        }
        if (!password.equals(confirm)) {
            throw new InvalidInputException("confirm", "As senhas não são iguais.");
        }
        // A leitura roda numa transação; o PBKDF2, que é a parte demorada, roda depois, fora dela.
        byte[] plain = readOnly.execute(status -> JSON.writeValueAsBytes(payload(userId)));
        try {
            byte[] file = BackupEncryptor.encrypt(plain, password);
            SecurityLog.event("exportacao_ok", username, ip);
            return file;
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    /** O conteúdo antes de criptografar: {"exportedAt": ..., "data": {...}}. Usado também nos testes. */
    ObjectNode payload(Long userId) {
        ObjectNode root = JSON.createObjectNode();
        root.put("exportedAt", Instant.now(clock).toString());
        ObjectNode data = root.putObject("data");

        // Metas e categorias primeiro: os ids delas são usados pelas categorias, contas e lançamentos.
        Map<Long, String> goalIds = new HashMap<>();
        ArrayNode goalArray = JSON.createArrayNode();
        for (Goal g : goals.findByUserIdOrderByIdAsc(userId)) {
            String id = Optional.ofNullable(g.getSystemKey()).flatMap(DefaultData::goalByKey)
                    .map(DefaultData.DefaultGoal::legacyId).orElse("g" + g.getId());
            goalIds.put(g.getId(), id);
            ObjectNode n = goalArray.addObject();
            n.put("id", id);
            n.put("name", g.getName());
            n.put("icon", g.getIcon());
            n.put("kind", goalKind(g.getKind()));
            n.put("target", g.getTarget());
            if (g.getDeadline() != null) {
                n.put("deadline", g.getDeadline().toString());
            }
            n.put("initial", g.getInitialBalance());
        }

        ArrayNode bucketArray = data.putArray("buckets");
        for (Bucket b : buckets.findByUserIdOrderBySortOrderAscIdAsc(userId)) {
            ObjectNode n = bucketArray.addObject();
            n.put("id", "b" + b.getId());
            n.put("name", b.getName());
            n.put("percent", b.getPercent());
            n.put("kind", bucketKind(b.getKind()));
            if (b.getDescription() != null) {
                n.put("description", b.getDescription());
            }
        }

        Map<Long, String> categoryIds = new HashMap<>();
        ArrayNode categoryArray = data.putArray("categories");
        for (Category c : categories.findByUserIdOrderByIdAsc(userId)) {
            String id = Optional.ofNullable(c.getSystemKey()).flatMap(DefaultData::categoryByKey)
                    .map(DefaultData.DefaultCategory::legacyId).orElse("c" + c.getId());
            categoryIds.put(c.getId(), id);
            ObjectNode n = categoryArray.addObject();
            n.put("id", id);
            n.put("name", c.getName());
            n.put("icon", c.getIcon());
            n.put("bucketId", "b" + c.getBucket().getId());
            n.put("kind", bucketKind(c.getKind()));
            if (c.getBudget() != null) {
                n.put("budget", c.getBudget());
            }
            putLink(n, "goalId", goalIds, c.getGoal() == null ? null : c.getGoal().getId());
            if (c.isArchived()) {
                n.put("archived", true);
            }
        }

        ArrayNode transactionArray = data.putArray("transactions");
        for (TransactionExportRow t : transactions.findForExport(userId)) {
            ObjectNode n = transactionArray.addObject();
            n.put("id", "t" + t.id());
            n.put("type", type(t.type()));
            // O mês de um lançamento de conta fixa é o mês da conta, que liga os dois na importação.
            n.put("month", (t.recurringMonth() != null ? t.recurringMonth() : YearMonth.from(t.date())).toString());
            n.put("date", t.date().toString());
            n.put("description", t.description());
            n.put("amount", t.amount());
            putLink(n, "categoryId", categoryIds, t.categoryId());
            putLink(n, "goalId", goalIds, t.goalId());
            if (t.recurringId() != null) {
                n.put("recurringId", "r" + t.recurringId());
            }
            n.put("paid", t.paid());
        }

        ArrayNode recurringArray = data.putArray("recurring");
        for (RecurringEntry r : recurring.findByUserIdOrderByIdAsc(userId)) {
            ObjectNode n = recurringArray.addObject();
            n.put("id", "r" + r.getId());
            n.put("type", type(r.getType()));
            n.put("description", r.getDescription());
            n.put("amount", r.getAmount());
            putLink(n, "categoryId", categoryIds, r.getCategory() == null ? null : r.getCategory().getId());
            putLink(n, "goalId", goalIds, r.getGoal() == null ? null : r.getGoal().getId());
            n.put("day", r.getDayOfMonth());
            n.put("startMonth", r.getStartMonth().toString());
            if (r.getEndMonth() != null) {
                n.put("endMonth", r.getEndMonth().toString());
            }
            n.put("active", r.isActive());
            ArrayNode skipped = n.putArray("skipped");
            r.getSkippedMonths().stream().sorted().forEach(m -> skipped.add(m.toString()));
        }

        data.set("goals", goalArray);
        profiles.findById(userId).ifPresent(p -> data.set("profile", profile(p)));
        data.set("settings", settings(settings.findById(userId).orElseThrow()));
        return root;
    }

    private static ObjectNode profile(InvestorProfile p) {
        ObjectNode n = JSON.createObjectNode();
        // As respostas do questionário nunca vêm para o servidor; só o resultado.
        n.putObject("answers");
        n.put("score", p.getScore());
        n.put("profile", switch (p.getProfile()) {
            case CONSERVATIVE -> "conservador";
            case MODERATE -> "moderado";
            case AGGRESSIVE -> "arrojado";
        });
        n.put("date", p.getTakenOn().toString());
        ObjectNode custom = n.putObject("custom");
        p.getCustomAllocation().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> custom.put(assetClass(e.getKey()), e.getValue()));
        return n;
    }

    private static ObjectNode settings(UserSettings s) {
        ObjectNode n = JSON.createObjectNode();
        n.put("userName", s.getDisplayName());
        if (s.getPresetId() != null) {
            n.put("presetId", s.getPresetId());
        }
        n.put("reserveMonths", s.getReserveMonths());
        n.put("reserveBase", reserveBase(s.getReserveBase()));
        n.put("reserveManualCost", s.getReserveManualCost());
        n.put("reservePriority", s.getReservePriority());
        ObjectNode sim = n.putObject("sim");
        sim.put("initial", s.getSimInitial());
        sim.put("monthly", s.getSimMonthly());
        sim.put("annualRate", s.getSimAnnualRate());
        sim.put("years", s.getSimYears());
        sim.put("inflation", s.getSimInflation());
        sim.put("yearlyIncrease", s.getSimYearlyIncrease());
        sim.put("target", s.getSimTarget());
        n.put("onboarded", s.isOnboarded());
        return n;
    }

    private static void putLink(ObjectNode n, String field, Map<Long, String> ids, Long id) {
        String exported = id == null ? null : ids.get(id);
        if (exported != null) {
            n.put(field, exported);
        }
    }

    // Os nomes do formato do app antigo. O caminho inverso fica no LegacyDataParser.

    private static String type(TransactionType type) {
        return switch (type) {
            case INCOME -> "receita";
            case EXPENSE -> "despesa";
        };
    }

    private static String bucketKind(BucketKind kind) {
        return switch (kind) {
            case NEEDS -> "necessidades";
            case WANTS -> "desejos";
            case FUTURE -> "futuro";
            case OTHER -> "outros";
        };
    }

    private static String goalKind(GoalKind kind) {
        return switch (kind) {
            case RESERVE -> "reserva";
            case INVESTMENT -> "investimento";
            case GOAL -> "meta";
        };
    }

    private static String reserveBase(ReserveBase base) {
        return switch (base) {
            case NEEDS -> "necessidades";
            case SPENDING -> "gastos";
            case MANUAL -> "manual";
        };
    }

    private static String assetClass(AssetClass assetClass) {
        return switch (assetClass) {
            case POST_FIXED -> "posfixado";
            case INFLATION_LINKED -> "inflacao";
            case PRE_FIXED -> "prefixado";
            case TAX_EXEMPT -> "isentos";
            case REAL_ESTATE_FUNDS -> "fiis";
            case STOCKS -> "acoes";
            case INTERNATIONAL -> "exterior";
        };
    }
}
