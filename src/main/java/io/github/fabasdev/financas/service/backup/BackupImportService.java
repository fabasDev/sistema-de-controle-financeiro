package io.github.fabasdev.financas.service.backup;

import io.github.fabasdev.financas.domain.Bucket;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.GoalKind;
import io.github.fabasdev.financas.domain.InvestorProfile;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.domain.User;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.InvestorProfileRepository;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import io.github.fabasdev.financas.security.LoginAttemptService;
import io.github.fabasdev.financas.security.SecurityLog;
import io.github.fabasdev.financas.service.DefaultData;
import io.github.fabasdev.financas.service.InvalidInputException;
import io.github.fabasdev.financas.service.UserDataEraser;
import io.github.fabasdev.financas.service.backup.LegacyDataParser.LBucket;
import io.github.fabasdev.financas.service.backup.LegacyDataParser.LCategory;
import io.github.fabasdev.financas.service.backup.LegacyDataParser.LGoal;
import io.github.fabasdev.financas.service.backup.LegacyDataParser.LRecurring;
import io.github.fabasdev.financas.service.backup.LegacyDataParser.LSettings;
import io.github.fabasdev.financas.service.backup.LegacyDataParser.LTransaction;
import io.github.fabasdev.financas.service.backup.LegacyDataParser.LegacyData;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * "Importar backup do app antigo": abre o arquivo exportado pelo app React e substitui os dados
 * da conta logada. Os ids do arquivo ("cat-moradia", uuids...) não vão para o banco: cada registro
 * ganha um id novo, e as ligações (categoria → pote, lançamento → conta fixa...) são refeitas
 * por um mapa id antigo → registro novo.
 */
@Service
public class BackupImportService {

    static final String SCOPE = "import";
    /** Tamanho máximo do arquivo (o mesmo do upload). */
    static final int MAX_BYTES = 10 * 1024 * 1024;

    public record ImportResult(int buckets, int categories, int goals, int recurring, int transactions,
            boolean profile, List<String> warnings) {
    }

    /** Números do JSON viram BigDecimal exato, nunca double. */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final BucketRepository buckets;
    private final CategoryRepository categories;
    private final GoalRepository goals;
    private final RecurringEntryRepository recurring;
    private final TransactionRepository transactions;
    private final InvestorProfileRepository profiles;
    private final UserDataEraser eraser;
    private final LoginAttemptService attempts;
    private final TransactionTemplate tx;

    public BackupImportService(UserRepository users, UserSettingsRepository settings, BucketRepository buckets,
            CategoryRepository categories, GoalRepository goals, RecurringEntryRepository recurring,
            TransactionRepository transactions, InvestorProfileRepository profiles, UserDataEraser eraser,
            LoginAttemptService attempts, PlatformTransactionManager txManager) {
        this.users = users;
        this.settings = settings;
        this.buckets = buckets;
        this.categories = categories;
        this.goals = goals;
        this.recurring = recurring;
        this.transactions = transactions;
        this.profiles = profiles;
        this.eraser = eraser;
        this.attempts = attempts;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Abre, valida e importa. A parte cara (PBKDF2) roda fora da transação do banco, e a gravação
     * roda numa transação só: se algo falhar, os dados atuais ficam como estavam.
     */
    public ImportResult importLegacy(Long userId, byte[] file, String password, String ip) {
        String username = users.findById(userId).map(User::getUsername).orElseThrow();
        if (attempts.blockedFor(SCOPE, username, ip).isPresent()) {
            throw new InvalidInputException("password", "Muitas tentativas. Espere alguns minutos e tente de novo.");
        }
        if (file == null || file.length == 0 || file.length > MAX_BYTES) {
            throw new InvalidInputException("file", "Escolha o arquivo .json do backup (até 10 MB).");
        }
        if (password == null || password.isEmpty() || password.length() > 1024) {
            throw new InvalidInputException("password", "Informe a senha do backup.");
        }
        LegacyBackupDecryptor.Envelope envelope = LegacyBackupDecryptor.readEnvelope(readJson(file));
        byte[] plain;
        try {
            plain = LegacyBackupDecryptor.decrypt(envelope, password);
        } catch (InvalidInputException e) {
            attempts.recordFailure(SCOPE, username, ip);
            SecurityLog.warn("importacao_senha_errada", username, ip);
            throw e;
        }
        attempts.recordSuccess(SCOPE, username, ip);
        LegacyData data = LegacyDataParser.parse(readJson(plain).get("data"));
        ImportResult result = tx.execute(status -> persist(userId, data));
        SecurityLog.event("importacao_ok", username, ip);
        return result;
    }

    private static JsonNode readJson(byte[] bytes) {
        try {
            return JSON.readTree(bytes);
        } catch (JacksonException e) {
            throw new InvalidInputException("file", LegacyBackupDecryptor.NOT_A_BACKUP);
        }
    }

    private ImportResult persist(Long userId, LegacyData data) {
        List<String> warnings = new ArrayList<>(data.warnings());
        User user = users.getReferenceById(userId);
        eraser.eraseFinancialData(userId);

        // Potes, na ordem do arquivo.
        Map<String, Bucket> bucketById = new HashMap<>();
        List<Bucket> bucketList = new ArrayList<>();
        int order = 0;
        for (LBucket b : data.buckets()) {
            Bucket bucket = buckets.save(new Bucket(user, order++, b.name(), b.percent(), b.kind(), b.description()));
            bucketById.put(b.id(), bucket);
            bucketList.add(bucket);
        }

        // Metas. As padrão do app antigo ganham a chave de sistema (a reserva não pode ser apagada).
        Map<String, Goal> goalById = new HashMap<>();
        Set<String> usedGoalKeys = new HashSet<>();
        for (LGoal g : data.goals()) {
            String key = DefaultData.goalByLegacyId(g.id()).map(DefaultData.DefaultGoal::key)
                    .filter(usedGoalKeys::add).orElse(null);
            Goal goal = new Goal(user, key, g.name(), g.icon(), g.kind());
            goal.setTarget(g.target());
            goal.setDeadline(g.deadline());
            goal.setInitialBalance(g.initial());
            goalById.put(g.id(), goals.save(goal));
        }
        if (!usedGoalKeys.contains(Goal.RESERVE_KEY)) {
            // O app precisa de uma reserva de emergência. Sem ela no arquivo, cria a padrão.
            DefaultData.DefaultGoal reserve = DefaultData.GOALS.getFirst();
            goals.save(new Goal(user, reserve.key(), reserve.name(), reserve.icon(), GoalKind.RESERVE));
        }

        // Categorias.
        Map<String, Category> categoryById = new HashMap<>();
        Set<String> usedCategoryKeys = new HashSet<>();
        boolean missingBucket = false;
        for (LCategory c : data.categories()) {
            Bucket bucket = c.bucketId() == null ? null : bucketById.get(c.bucketId());
            if (bucket == null) {
                bucket = bucketList.getFirst();
                missingBucket = true;
            }
            String key = DefaultData.categoryByLegacyId(c.id()).map(DefaultData.DefaultCategory::key)
                    .filter(usedCategoryKeys::add).orElse(null);
            Category category = new Category(user, key, c.name(), c.icon(), bucket, c.kind());
            category.setBudget(c.budget());
            category.setGoal(c.goalId() == null ? null : goalById.get(c.goalId()));
            category.setArchived(c.archived());
            categoryById.put(c.id(), categories.save(category));
        }
        if (missingBucket) {
            warnings.add("Algumas categorias apontavam para um pote que não existe e foram para o primeiro pote.");
        }

        // Contas fixas.
        Map<String, RecurringEntry> recurringById = new HashMap<>();
        int brokenLinks = 0;
        for (LRecurring r : data.recurring()) {
            RecurringEntry entry = new RecurringEntry(user, r.type(), r.description(), r.amount(), r.day(),
                    r.startMonth());
            entry.setEndMonth(r.endMonth());
            entry.setActive(r.active());
            if (r.type() == TransactionType.EXPENSE) {
                entry.setCategory(link(categoryById, r.categoryId()));
                entry.setGoal(link(goalById, r.goalId()));
                brokenLinks += broken(categoryById, r.categoryId()) + broken(goalById, r.goalId());
            }
            entry.getSkippedMonths().addAll(r.skipped());
            recurringById.put(r.id(), recurring.save(entry));
        }

        // Lançamentos. Cada mês de uma conta fixa só pode ter um lançamento ligado a ela.
        Set<String> usedRecurringMonths = new HashSet<>();
        List<Transaction> batch = new ArrayList<>(data.transactions().size());
        for (LTransaction t : data.transactions()) {
            Transaction entity = new Transaction(user, t.type(), t.date(), t.description(), t.amount(), t.paid());
            if (t.type() == TransactionType.EXPENSE) {
                entity.setCategory(link(categoryById, t.categoryId()));
                entity.setGoal(link(goalById, t.goalId()));
                brokenLinks += broken(categoryById, t.categoryId()) + broken(goalById, t.goalId());
            }
            RecurringEntry r = link(recurringById, t.recurringId());
            YearMonth month = t.month();
            if (r != null && usedRecurringMonths.add(t.recurringId() + "|" + month)) {
                entity.linkRecurring(r, month);
            }
            batch.add(entity);
        }
        transactions.saveAll(batch);
        if (brokenLinks > 0) {
            warnings.add(brokenLinks + " ligação(ões) com categoria ou meta inexistente ficaram em branco.");
        }

        applySettings(userId, data.settings());
        boolean hasProfile = data.profile() != null;
        if (hasProfile) {
            InvestorProfile p = new InvestorProfile(user, data.profile().profile(), data.profile().score(),
                    data.profile().takenOn());
            p.getCustomAllocation().putAll(data.profile().custom());
            profiles.save(p);
        }
        return new ImportResult(bucketList.size(), categoryById.size(), goalById.size(), recurringById.size(),
                batch.size(), hasProfile, List.copyOf(warnings));
    }

    private void applySettings(Long userId, LSettings s) {
        UserSettings current = settings.findById(userId).orElseThrow();
        if (s.userName() != null) {
            current.setDisplayName(s.userName());
        }
        current.setPresetId(s.presetId() != null ? s.presetId() : UserSettings.CUSTOM_PRESET);
        if (s.reserveMonths() != null) {
            current.setReserveMonths(s.reserveMonths());
        }
        if (s.reserveBase() != null) {
            current.setReserveBase(s.reserveBase());
        }
        if (s.reserveManualCost() != null) {
            current.setReserveManualCost(s.reserveManualCost());
        }
        if (s.reservePriority() != null) {
            current.setReservePriority(s.reservePriority());
        }
        if (s.simInitial() != null) {
            current.setSimInitial(s.simInitial());
        }
        if (s.simMonthly() != null) {
            current.setSimMonthly(s.simMonthly());
        }
        if (s.simAnnualRate() != null) {
            current.setSimAnnualRate(s.simAnnualRate());
        }
        if (s.simYears() != null) {
            current.setSimYears(s.simYears());
        }
        if (s.simInflation() != null) {
            current.setSimInflation(s.simInflation());
        }
        if (s.simYearlyIncrease() != null) {
            current.setSimYearlyIncrease(s.simYearlyIncrease());
        }
        if (s.simTarget() != null) {
            current.setSimTarget(s.simTarget());
        }
        if (s.onboarded() != null) {
            current.setOnboarded(s.onboarded());
        }
    }

    private static <T> T link(Map<String, T> byId, String legacyId) {
        return legacyId == null ? null : byId.get(legacyId);
    }

    private static int broken(Map<String, ?> byId, String legacyId) {
        return legacyId != null && !byId.containsKey(legacyId) ? 1 : 0;
    }
}
