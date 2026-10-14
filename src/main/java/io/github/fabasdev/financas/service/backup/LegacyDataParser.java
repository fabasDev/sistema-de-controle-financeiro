package io.github.fabasdev.financas.service.backup;

import io.github.fabasdev.financas.domain.AssetClass;
import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.GoalKind;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.domain.ReserveBase;
import io.github.fabasdev.financas.domain.RiskProfile;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.service.InvalidInputException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Valida o conteúdo do backup antigo campo a campo (o mesmo esquema do zod em backup.ts, com os
 * limites do banco novo). Registro com problema é ignorado e vira um aviso. O conteúdo não é
 * confiável: pode ter sido editado à mão.
 */
public final class LegacyDataParser {

    static final int MAX_BUCKETS = 12;
    static final int MAX_CATEGORIES = 500;
    static final int MAX_GOALS = 200;
    static final int MAX_RECURRING = 1_000;
    static final int MAX_TRANSACTIONS = 50_000;
    static final int MAX_SKIPPED = 1_200;

    private static final Pattern MONTH = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern ICON = Pattern.compile("^[A-Za-z0-9]{1,40}$");

    public record LBucket(String id, String name, BigDecimal percent, BucketKind kind, String description) {
    }

    public record LCategory(String id, String name, String icon, String bucketId, BucketKind kind, BigDecimal budget,
            String goalId, boolean archived) {
    }

    public record LGoal(String id, String name, String icon, GoalKind kind, BigDecimal target, YearMonth deadline,
            BigDecimal initial) {
    }

    public record LRecurring(String id, TransactionType type, String description, BigDecimal amount,
            String categoryId, String goalId, int day, YearMonth startMonth, YearMonth endMonth, boolean active,
            Set<YearMonth> skipped) {
    }

    public record LTransaction(String id, TransactionType type, YearMonth month, LocalDate date, String description,
            BigDecimal amount, String categoryId, String goalId, String recurringId, boolean paid) {
    }

    public record LProfile(RiskProfile profile, int score, LocalDate takenOn, Map<AssetClass, BigDecimal> custom) {
    }

    /** Configurações. Campo ausente ou inválido fica null e mantém o valor atual. */
    public record LSettings(String userName, String presetId, Integer reserveMonths, ReserveBase reserveBase,
            BigDecimal reserveManualCost, BigDecimal reservePriority, BigDecimal simInitial, BigDecimal simMonthly,
            BigDecimal simAnnualRate, Integer simYears, BigDecimal simInflation, BigDecimal simYearlyIncrease,
            BigDecimal simTarget, Boolean onboarded) {
    }

    public record LegacyData(List<LBucket> buckets, List<LCategory> categories, List<LGoal> goals,
            List<LRecurring> recurring, List<LTransaction> transactions, LProfile profile, LSettings settings,
            List<String> warnings) {
    }

    private final List<String> warnings = new ArrayList<>();

    private LegacyDataParser() {
    }

    public static LegacyData parse(JsonNode data) {
        if (data == null || !data.isObject()) {
            throw new InvalidInputException("file", "O conteúdo do backup está incompleto ou corrompido.");
        }
        return new LegacyDataParser().run(data);
    }

    private LegacyData run(JsonNode data) {
        List<LBucket> buckets = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode n : array(data, "buckets", MAX_BUCKETS)) {
            String id = id(n);
            BigDecimal percent = number(n.get("percent"));
            BucketKind kind = bucketKind(n.get("kind"));
            if (id == null || !ids.add("b:" + id) || percent == null || percent.signum() < 0
                    || percent.compareTo(Money.HUNDRED) > 0 || kind == null) {
                warn("Um pote com dados inválidos foi ignorado.");
                continue;
            }
            buckets.add(new LBucket(id, name(n.get("name"), 60, "Pote"), percent, kind,
                    optionalText(n.get("description"), 500)));
        }
        if (buckets.isEmpty()) {
            throw new InvalidInputException("file", "O backup não tem nenhum pote válido na regra de distribuição.");
        }

        List<LGoal> goals = new ArrayList<>();
        for (JsonNode n : array(data, "goals", MAX_GOALS)) {
            String id = id(n);
            GoalKind kind = goalKind(n.get("kind"));
            BigDecimal target = money(n.get("target"), false);
            BigDecimal initial = money(n.get("initial"), false);
            if (id == null || !ids.add("g:" + id) || kind == null || target == null || initial == null) {
                warn("Uma meta com dados inválidos foi ignorada.");
                continue;
            }
            goals.add(new LGoal(id, name(n.get("name"), 60, "Meta"), icon(n.get("icon")), kind, target,
                    month(n.get("deadline")), initial));
        }

        List<LCategory> categories = new ArrayList<>();
        for (JsonNode n : array(data, "categories", MAX_CATEGORIES)) {
            String id = id(n);
            BucketKind kind = bucketKind(n.get("kind"));
            JsonNode budgetNode = n.get("budget");
            BigDecimal budget = budgetNode == null || budgetNode.isNull() ? null : money(budgetNode, false);
            if (id == null || !ids.add("c:" + id) || kind == null || (budgetNode != null && !budgetNode.isNull() && budget == null)) {
                warn("Uma categoria com dados inválidos foi ignorada.");
                continue;
            }
            categories.add(new LCategory(id, name(n.get("name"), 60, "Categoria"), icon(n.get("icon")),
                    optionalText(n.get("bucketId"), 200), kind, budget == null || budget.signum() == 0 ? null : budget,
                    optionalText(n.get("goalId"), 200), n.path("archived").isBoolean() && n.path("archived").asBoolean()));
        }

        List<LRecurring> recurring = new ArrayList<>();
        for (JsonNode n : array(data, "recurring", MAX_RECURRING)) {
            String id = id(n);
            TransactionType type = txType(n.get("type"));
            BigDecimal amount = money(n.get("amount"), true);
            YearMonth start = month(n.get("startMonth"));
            YearMonth end = month(n.get("endMonth"));
            JsonNode day = n.get("day");
            boolean dayOk = day != null && day.isIntegralNumber() && day.canConvertToInt() && day.asInt() >= 1
                    && day.asInt() <= 31;
            if (id == null || !ids.add("r:" + id) || type == null || amount == null || start == null || !dayOk
                    || (end != null && end.isBefore(start)) || !n.path("active").isBoolean()) {
                warn("Uma conta fixa com dados inválidos foi ignorada.");
                continue;
            }
            Set<YearMonth> skipped = new LinkedHashSet<>();
            JsonNode skippedNode = n.path("skipped");
            if (skippedNode.isArray() && skippedNode.size() <= MAX_SKIPPED) {
                for (JsonNode m : skippedNode.values()) {
                    YearMonth ym = month(m);
                    if (ym != null) {
                        skipped.add(ym);
                    }
                }
            }
            recurring.add(new LRecurring(id, type, text(n.get("description"), 200, ""), amount,
                    optionalText(n.get("categoryId"), 200), optionalText(n.get("goalId"), 200), day.asInt(), start, end,
                    n.path("active").asBoolean(), skipped));
        }

        List<LTransaction> transactions = new ArrayList<>();
        int invalidTransactions = 0;
        for (JsonNode n : array(data, "transactions", MAX_TRANSACTIONS)) {
            String id = id(n);
            TransactionType type = txType(n.get("type"));
            BigDecimal amount = money(n.get("amount"), true);
            LocalDate date = date(n.get("date"));
            YearMonth month = month(n.get("month"));
            if (id == null || !ids.add("t:" + id) || type == null || amount == null || date == null
                    || !n.path("paid").isBoolean()) {
                invalidTransactions++;
                continue;
            }
            transactions.add(new LTransaction(id, type, month == null ? YearMonth.from(date) : month, date,
                    text(n.get("description"), 200, ""), amount, optionalText(n.get("categoryId"), 200),
                    optionalText(n.get("goalId"), 200), optionalText(n.get("recurringId"), 200),
                    n.path("paid").asBoolean()));
        }
        if (invalidTransactions > 0) {
            warn(invalidTransactions + " lançamento(s) com dados inválidos (valor, data ou tipo) foram ignorados.");
        }

        return new LegacyData(buckets, categories, goals, recurring, transactions, profile(data.get("profile")),
                settings(data.path("settings")), List.copyOf(warnings));
    }

    private LProfile profile(JsonNode n) {
        if (n == null || n.isNull() || !n.isObject()) {
            return null;
        }
        RiskProfile profile = switch (string(n.get("profile"))) {
            case "conservador" -> RiskProfile.CONSERVATIVE;
            case "moderado" -> RiskProfile.MODERATE;
            case "arrojado" -> RiskProfile.AGGRESSIVE;
            case null, default -> null;
        };
        JsonNode score = n.get("score");
        String dateText = string(n.get("date"));
        LocalDate takenOn = null;
        if (dateText != null && dateText.length() >= 10 && DATE.matcher(dateText.substring(0, 10)).matches()) {
            takenOn = parseDate(dateText.substring(0, 10));
        }
        if (profile == null || score == null || !score.isNumber() || score.asInt() < 0 || score.asInt() > 25
                || takenOn == null) {
            warn("O perfil de investidor do backup estava incompleto e não foi importado.");
            return null;
        }
        Map<AssetClass, BigDecimal> custom = new EnumMap<>(AssetClass.class);
        JsonNode c = n.path("custom");
        if (c.isObject()) {
            for (Map.Entry<String, JsonNode> e : c.properties()) {
                AssetClass assetClass = assetClass(e.getKey());
                BigDecimal value = number(e.getValue());
                if (assetClass != null && value != null && value.signum() >= 0 && value.compareTo(Money.HUNDRED) <= 0) {
                    custom.put(assetClass, value.setScale(2, Money.ROUNDING));
                }
            }
        }
        return new LProfile(profile, score.asInt(), takenOn, custom);
    }

    private LSettings settings(JsonNode s) {
        if (!s.isObject()) {
            return new LSettings(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
        String preset = string(s.get("presetId"));
        if (preset != null && !Set.of("50-30-20", "70-20-10", "6-potes", "80-20", "custom").contains(preset)) {
            preset = null;
        }
        Integer reserveMonths = intIn(s.get("reserveMonths"), 1, 60);
        ReserveBase base = switch (string(s.get("reserveBase"))) {
            case "necessidades" -> ReserveBase.NEEDS;
            case "gastos" -> ReserveBase.SPENDING;
            case "manual" -> ReserveBase.MANUAL;
            case null, default -> null;
        };
        JsonNode sim = s.path("sim");
        return new LSettings(
                s.get("userName") == null ? null : name(s.get("userName"), 60, ""),
                preset, reserveMonths, base,
                money(s.get("reserveManualCost"), false),
                percent(s.get("reservePriority")),
                money(sim.get("initial"), false),
                money(sim.get("monthly"), false),
                percent(sim.get("annualRate")),
                intIn(sim.get("years"), 1, 50),
                percent(sim.get("inflation")),
                percent(sim.get("yearlyIncrease")),
                money(sim.get("target"), false),
                s.path("onboarded").isBoolean() ? s.path("onboarded").asBoolean() : null);
    }

    private Iterable<JsonNode> array(JsonNode data, String field, int max) {
        JsonNode node = data.get(field);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new InvalidInputException("file", "O conteúdo do backup está incompleto ou corrompido.");
        }
        if (node.size() > max) {
            throw new InvalidInputException("file", "O backup tem registros demais (" + field + ": " + node.size()
                    + ", máximo " + max + ").");
        }
        return node.values();
    }

    private void warn(String message) {
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    private static String id(JsonNode n) {
        String id = n == null || !n.isObject() ? null : string(n.get("id"));
        return id == null || id.isEmpty() || id.length() > 200 ? null : id;
    }

    private static String string(JsonNode node) {
        return node != null && node.isString() ? node.stringValue() : null;
    }

    /** Nome obrigatório: corta no tamanho da coluna e usa um padrão se vier vazio. */
    private String name(JsonNode node, int max, String fallback) {
        String s = string(node);
        if (s == null || s.isBlank()) {
            return fallback;
        }
        s = s.strip();
        if (s.length() > max) {
            warn("Alguns nomes longos foram cortados em " + max + " caracteres.");
            s = s.substring(0, max);
        }
        return s;
    }

    private String text(JsonNode node, int max, String fallback) {
        String s = string(node);
        if (s == null) {
            return fallback;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String optionalText(JsonNode node, int max) {
        String s = string(node);
        return s == null || s.isEmpty() ? null : (s.length() > max ? s.substring(0, max) : s);
    }

    private static String icon(JsonNode node) {
        String s = string(node);
        return s != null && ICON.matcher(s).matches() ? s : "Tag";
    }

    /** Número do JSON lido como decimal exato (sem passar por double). */
    private static BigDecimal number(JsonNode node) {
        return node != null && node.isNumber() ? node.decimalValue() : null;
    }

    /** Dinheiro com 2 casas (HALF_UP). {@code positive}: precisa ser maior que zero. */
    private static BigDecimal money(JsonNode node, boolean positive) {
        BigDecimal v = number(node);
        if (v == null) {
            return null;
        }
        v = Money.round(v);
        if ((positive ? v.signum() <= 0 : v.signum() < 0) || v.compareTo(Money.MAX) > 0) {
            return null;
        }
        return v;
    }

    private static BigDecimal percent(JsonNode node) {
        BigDecimal v = number(node);
        return v == null || v.signum() < 0 || v.compareTo(Money.HUNDRED) > 0 ? null : v.setScale(2, Money.ROUNDING);
    }

    private static Integer intIn(JsonNode node, int min, int max) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            return null;
        }
        int v = node.asInt();
        return v < min || v > max ? null : v;
    }

    private static YearMonth month(JsonNode node) {
        String s = string(node);
        if (s == null || !MONTH.matcher(s).matches()) {
            return null;
        }
        try {
            YearMonth m = YearMonth.parse(s);
            return m.getYear() < 2000 || m.getYear() > 2100 ? null : m;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate date(JsonNode node) {
        String s = string(node);
        return s == null || !DATE.matcher(s).matches() ? null : parseDate(s);
    }

    private static LocalDate parseDate(String s) {
        try {
            LocalDate d = LocalDate.parse(s);
            return d.getYear() < 2000 || d.getYear() > 2100 ? null : d;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BucketKind bucketKind(JsonNode node) {
        return switch (string(node)) {
            case "necessidades" -> BucketKind.NEEDS;
            case "desejos" -> BucketKind.WANTS;
            case "futuro" -> BucketKind.FUTURE;
            case "outros" -> BucketKind.OTHER;
            case null, default -> null;
        };
    }

    private static GoalKind goalKind(JsonNode node) {
        return switch (string(node)) {
            case "reserva" -> GoalKind.RESERVE;
            case "investimento" -> GoalKind.INVESTMENT;
            case "meta" -> GoalKind.GOAL;
            case null, default -> null;
        };
    }

    private static TransactionType txType(JsonNode node) {
        return switch (string(node)) {
            case "receita" -> TransactionType.INCOME;
            case "despesa" -> TransactionType.EXPENSE;
            case null, default -> null;
        };
    }

    private static AssetClass assetClass(String key) {
        return switch (key) {
            case "posfixado" -> AssetClass.POST_FIXED;
            case "inflacao" -> AssetClass.INFLATION_LINKED;
            case "prefixado" -> AssetClass.PRE_FIXED;
            case "isentos" -> AssetClass.TAX_EXEMPT;
            case "fiis" -> AssetClass.REAL_ESTATE_FUNDS;
            case "acoes" -> AssetClass.STOCKS;
            case "exterior" -> AssetClass.INTERNATIONAL;
            default -> null;
        };
    }
}
