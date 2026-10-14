package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.GoalKind;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.domain.Months;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.domain.User;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seis meses de dados fictícios para explorar o app. Porte de createSampleData (sample.ts).
 * Substitui os dados atuais da conta (a tela pede confirmação). Os valores "aleatórios" vêm de um
 * gerador com semente fixa, então o exemplo sai sempre igual, e são calculados em BigDecimal.
 */
@Service
public class SampleDataService {

    private record Rec(TransactionType type, String description, String amount, String categoryKey, String goalKey,
            int day, int startOffset, Integer endOffset) {
    }

    private static final List<Rec> RECURRING = List.of(
            new Rec(TransactionType.INCOME, "Salário", "6500", null, null, 5, 0, null),
            new Rec(TransactionType.EXPENSE, "Aluguel", "1650", "housing", null, 10, 0, null),
            new Rec(TransactionType.EXPENSE, "Condomínio", "420", "housing", null, 10, 0, null),
            new Rec(TransactionType.EXPENSE, "Internet", "119.90", "utilities", null, 18, 0, null),
            new Rec(TransactionType.EXPENSE, "Celular", "59.90", "utilities", null, 18, 0, null),
            new Rec(TransactionType.EXPENSE, "Academia", "109.90", "health", null, 8, 0, null),
            new Rec(TransactionType.EXPENSE, "Streaming de vídeo e música", "76.80", "subscriptions", null, 12, 0, null),
            new Rec(TransactionType.EXPENSE, "Aporte na reserva", "600", "emergency_reserve", Goal.RESERVE_KEY, 6, 0, null),
            new Rec(TransactionType.EXPENSE, "Aporte mensal (Tesouro/CDB)", "400", "investments", Goal.INVESTMENTS_KEY, 6, 0,
                    null),
            new Rec(TransactionType.EXPENSE, "Curso de inglês (parcela)", "289", "education", null, 15, 2, 9));

    private final UserDataEraser eraser;
    private final UserDataProvisioner provisioner;
    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final CategoryRepository categories;
    private final GoalRepository goals;
    private final RecurringEntryRepository recurring;
    private final TransactionRepository transactions;
    private final Clock clock;

    public SampleDataService(UserDataEraser eraser, UserDataProvisioner provisioner, UserRepository users,
            UserSettingsRepository settings, CategoryRepository categories, GoalRepository goals,
            RecurringEntryRepository recurring, TransactionRepository transactions, Clock clock) {
        this.eraser = eraser;
        this.provisioner = provisioner;
        this.users = users;
        this.settings = settings;
        this.categories = categories;
        this.goals = goals;
        this.recurring = recurring;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional
    public void load(Long userId) {
        eraser.eraseFinancialData(userId);
        settings.deleteAllByUserId(userId);
        settings.flush();
        User user = users.getReferenceById(userId);
        provisioner.provision(user);

        LocalDate today = LocalDate.now(clock);
        YearMonth now = YearMonth.from(today);
        List<YearMonth> months = Months.range(now, 6);
        YearMonth start = months.getFirst();
        Random random = new Random(42);

        goal(userId, Goal.RESERVE_KEY).setInitialBalance(new BigDecimal("4800"));
        goal(userId, Goal.INVESTMENTS_KEY).setInitialBalance(new BigDecimal("7200"));
        Goal trip = newGoal(user, "Viagem de férias", "Plane", "9000", now.plusMonths(9), "1500");
        Goal laptop = newGoal(user, "Notebook novo", "Laptop", "6000", now.plusMonths(5), "2200");

        List<RecurringEntry> entries = new ArrayList<>();
        for (Rec r : RECURRING) {
            RecurringEntry e = new RecurringEntry(user, r.type(), r.description(), new BigDecimal(r.amount()), r.day(),
                    start.plusMonths(r.startOffset()));
            if (r.endOffset() != null) {
                e.setEndMonth(start.plusMonths(r.endOffset()));
            }
            e.setCategory(r.categoryKey() == null ? null : category(userId, r.categoryKey()));
            e.setGoal(r.goalKey() == null ? null : goal(userId, r.goalKey()));
            entries.add(recurring.save(e));
        }

        List<Transaction> batch = new ArrayList<>();
        for (YearMonth month : months) {
            for (RecurringEntry e : entries) {
                if (!e.coversMonth(month)) {
                    continue;
                }
                LocalDate date = Months.dateInMonth(month, e.getDayOfMonth());
                Transaction t = new Transaction(user, e.getType(), date, e.getDescription(), e.getAmount(),
                        !date.isAfter(today));
                t.setCategory(e.getCategory());
                t.setGoal(e.getGoal());
                t.linkRecurring(e, month);
                batch.add(t);
            }
            Adder add = (day, description, key, amount, goal) -> {
                LocalDate date = Months.dateInMonth(month, day);
                if (date.isAfter(today)) {
                    return;
                }
                Transaction t = new Transaction(user, TransactionType.EXPENSE, date, description, amount, true);
                t.setCategory(category(userId, key));
                t.setGoal(goal);
                batch.add(t);
            };
            add.add(3, "Supermercado", "groceries", random.between(380, 520), null);
            add.add(11, "Feira e hortifruti", "groceries", random.between(90, 160), null);
            add.add(17, "Supermercado", "groceries", random.between(300, 460), null);
            add.add(25, "Padaria e açougue", "groceries", random.between(120, 210), null);
            add.add(14, "Energia elétrica", "utilities", random.between(150, 230), null);
            add.add(14, "Água", "utilities", random.between(70, 110), null);
            add.add(7, "Combustível", "transport", random.between(220, 320), null);
            add.add(21, "Combustível", "transport", random.between(180, 280), null);
            add.add(9, "Aplicativo de transporte", "transport", random.between(40, 90), null);
            add.add(13, "Farmácia", "health", random.between(45, 140), null);
            add.add(6, "Restaurante", "restaurants", random.between(90, 180), null);
            add.add(16, "Delivery", "restaurants", random.between(50, 95), null);
            add.add(27, "Delivery", "restaurants", random.between(45, 90), null);
            add.add(19, "Cinema", "leisure", random.between(60, 120), null);
            add.add(23, "Bar com amigos", "leisure", random.between(80, 190), null);
            add.add(20, "Cabeleireiro", "personal_care", random.between(50, 90), null);
            if (random.chance(40)) {
                add.add(24, "Roupas", "shopping", random.between(150, 420), null);
            }
            if (random.chance(60)) {
                add.add(26, "Presente de aniversário", "gifts", random.between(80, 200), null);
            }
            if (random.chance(50)) {
                add.add(28, "Guardar para a viagem", "goals", new BigDecimal("350"), trip);
            }
            if (random.chance(50)) {
                add.add(28, "Guardar para o notebook", "goals", new BigDecimal("300"), laptop);
            }
            if (random.chance(55)) {
                LocalDate date = Months.dateInMonth(month, 22);
                if (!date.isAfter(today)) {
                    batch.add(new Transaction(user, TransactionType.INCOME, date, "Freelance",
                            random.between(600, 1400), month.isBefore(now)));
                }
            }
        }
        transactions.saveAll(batch);

        UserSettings s = settings.findById(userId).orElseThrow();
        s.setDisplayName("Visitante");
        s.setOnboarded(true);
    }

    @FunctionalInterface
    private interface Adder {
        void add(int day, String description, String categoryKey, BigDecimal amount, Goal goal);
    }

    private Category category(Long userId, String key) {
        return categories.findByUserIdAndSystemKey(userId, key).orElseThrow();
    }

    private Goal goal(Long userId, String key) {
        return goals.findByUserIdAndSystemKey(userId, key).orElseThrow();
    }

    private Goal newGoal(User user, String name, String icon, String target, YearMonth deadline, String initial) {
        Goal g = new Goal(user, null, name, icon, GoalKind.GOAL);
        g.setTarget(new BigDecimal(target));
        g.setDeadline(deadline);
        g.setInitialBalance(new BigDecimal(initial));
        return goals.save(g);
    }

    /** Gerador congruencial linear (o mesmo de sample.ts), com as contas em long e BigDecimal. */
    private static final class Random {
        private static final BigDecimal RANGE = BigDecimal.valueOf(4_294_967_296L);
        private long seed;

        Random(long seed) {
            this.seed = seed;
        }

        /** Número em [0, 1) como BigDecimal. */
        private BigDecimal next() {
            seed = (seed * 1_664_525L + 1_013_904_223L) % 4_294_967_296L;
            return BigDecimal.valueOf(seed).divide(RANGE, Money.PRECISION);
        }

        BigDecimal between(int min, int max) {
            BigDecimal span = BigDecimal.valueOf(max - min);
            return Money.round(BigDecimal.valueOf(min).add(next().multiply(span)));
        }

        /** Verdadeiro em (100 − percent)% das vezes, como {@code rand() > percent/100} no app antigo. */
        boolean chance(int percent) {
            return next().compareTo(BigDecimal.valueOf(percent).divide(Money.HUNDRED, 2, RoundingMode.UNNECESSARY)) > 0;
        }
    }
}
