package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.GoalKind;
import java.util.List;
import java.util.Optional;

/**
 * Categorias e metas criadas para todo usuário novo. Porte de RAW_CATEGORIES e DEFAULT_GOALS
 * (legacy-web/src/lib/presets.ts). O legacyId serve para importar o backup do app antigo.
 */
public final class DefaultData {

    public static final String DEFAULT_PRESET = "50-30-20";

    public record DefaultCategory(String key, String legacyId, String name, String icon, BucketKind kind,
            String goalKey) {
    }

    public record DefaultGoal(String key, String legacyId, String name, String icon, GoalKind kind) {
    }

    public static final List<DefaultCategory> CATEGORIES = List.of(
            new DefaultCategory("housing", "cat-moradia", "Moradia", "House", BucketKind.NEEDS, null),
            new DefaultCategory("utilities", "cat-contas", "Contas da casa", "Zap", BucketKind.NEEDS, null),
            new DefaultCategory("groceries", "cat-mercado", "Mercado", "ShoppingCart", BucketKind.NEEDS, null),
            new DefaultCategory("transport", "cat-transporte", "Transporte", "Car", BucketKind.NEEDS, null),
            new DefaultCategory("health", "cat-saude", "Saúde", "HeartPulse", BucketKind.NEEDS, null),
            new DefaultCategory("education", "cat-educacao", "Educação", "GraduationCap", BucketKind.NEEDS, null),
            new DefaultCategory("restaurants", "cat-restaurantes", "Restaurantes e delivery", "UtensilsCrossed",
                    BucketKind.WANTS, null),
            new DefaultCategory("leisure", "cat-lazer", "Lazer", "Gamepad2", BucketKind.WANTS, null),
            new DefaultCategory("subscriptions", "cat-assinaturas", "Assinaturas", "Tv", BucketKind.WANTS, null),
            new DefaultCategory("shopping", "cat-compras", "Compras", "ShoppingBag", BucketKind.WANTS, null),
            new DefaultCategory("travel", "cat-viagens", "Viagens", "Plane", BucketKind.WANTS, null),
            new DefaultCategory("personal_care", "cat-cuidados", "Cuidados pessoais", "Sparkles", BucketKind.WANTS,
                    null),
            new DefaultCategory("emergency_reserve", "cat-reserva", "Reserva de emergência", "ShieldCheck",
                    BucketKind.FUTURE, Goal.RESERVE_KEY),
            new DefaultCategory("investments", "cat-investimentos", "Investimentos", "TrendingUp", BucketKind.FUTURE,
                    Goal.INVESTMENTS_KEY),
            new DefaultCategory("goals", "cat-metas", "Metas e sonhos", "Target", BucketKind.FUTURE, null),
            new DefaultCategory("gifts", "cat-presentes", "Presentes e doações", "Gift", BucketKind.OTHER, null),
            new DefaultCategory("other", "cat-outros", "Outros", "Ellipsis", BucketKind.OTHER, null));

    public static final List<DefaultGoal> GOALS = List.of(
            new DefaultGoal(Goal.RESERVE_KEY, "goal-reserva", "Reserva de emergência", "ShieldCheck",
                    GoalKind.RESERVE),
            new DefaultGoal(Goal.INVESTMENTS_KEY, "goal-investimentos", "Carteira de investimentos", "TrendingUp",
                    GoalKind.INVESTMENT));

    private DefaultData() {
    }

    public static Optional<DefaultCategory> categoryByLegacyId(String legacyId) {
        return CATEGORIES.stream().filter(c -> c.legacyId().equals(legacyId)).findFirst();
    }

    public static Optional<DefaultGoal> goalByLegacyId(String legacyId) {
        return GOALS.stream().filter(g -> g.legacyId().equals(legacyId)).findFirst();
    }

    /** O caminho inverso, para o backup exportado: a chave de sistema vira o id do app antigo. */
    public static Optional<DefaultCategory> categoryByKey(String key) {
        return CATEGORIES.stream().filter(c -> c.key().equals(key)).findFirst();
    }

    public static Optional<DefaultGoal> goalByKey(String key) {
        return GOALS.stream().filter(g -> g.key().equals(key)).findFirst();
    }
}
