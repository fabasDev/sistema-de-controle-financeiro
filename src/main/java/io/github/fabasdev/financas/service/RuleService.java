package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.Bucket;
import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.domain.RulePreset;
import io.github.fabasdev.financas.domain.User;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.RulePresetRepository;
import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regra de distribuição da renda (potes). A regra fica no banco e é editável por usuário,
 * em vez de escondida numa tela. Porte de presets.ts e das ações de potes de store.ts.
 */
@Service
public class RuleService {

    public static final int MAX_BUCKETS = 6;

    /** Para onde vai uma categoria quando a regra não tem um pote do mesmo tipo. */
    private static final Map<BucketKind, List<BucketKind>> KIND_FALLBACK = Map.of(
            BucketKind.NEEDS, List.of(BucketKind.NEEDS, BucketKind.WANTS, BucketKind.OTHER, BucketKind.FUTURE),
            BucketKind.WANTS, List.of(BucketKind.WANTS, BucketKind.NEEDS, BucketKind.OTHER, BucketKind.FUTURE),
            BucketKind.FUTURE, List.of(BucketKind.FUTURE, BucketKind.OTHER, BucketKind.NEEDS, BucketKind.WANTS),
            BucketKind.OTHER, List.of(BucketKind.OTHER, BucketKind.WANTS, BucketKind.NEEDS, BucketKind.FUTURE));

    /** Pote recém-criado a partir de uma regra, com as categorias que ele "puxa". */
    public record BucketWithClaims(Bucket bucket, Set<String> claims) {
    }

    private final RulePresetRepository presets;
    private final BucketRepository buckets;
    private final CategoryRepository categories;
    private final UserSettingsRepository settings;
    private final UserRepository users;

    public RuleService(RulePresetRepository presets, BucketRepository buckets, CategoryRepository categories,
            UserSettingsRepository settings, UserRepository users) {
        this.presets = presets;
        this.buckets = buckets;
        this.categories = categories;
        this.settings = settings;
        this.users = users;
    }

    /**
     * Escolhe o pote de uma categoria: primeiro o que a reivindica pela chave,
     * depois o primeiro do mesmo tipo (ou do tipo mais próximo), senão o primeiro pote.
     */
    public static Bucket bucketFor(String categoryKey, BucketKind kind, List<BucketWithClaims> options) {
        if (categoryKey != null) {
            for (BucketWithClaims option : options) {
                if (option.claims().contains(categoryKey)) {
                    return option.bucket();
                }
            }
        }
        for (BucketKind candidate : KIND_FALLBACK.get(kind)) {
            for (BucketWithClaims option : options) {
                if (option.bucket().getKind() == candidate) {
                    return option.bucket();
                }
            }
        }
        return options.getFirst().bucket();
    }

    /**
     * Ajusta os percentuais para somarem exatamente 100, mantendo a proporção.
     * O que sobrar do arredondamento vai para o maior pote.
     */
    public static List<BigDecimal> normalizePercents(List<BigDecimal> percents) {
        int n = percents.size();
        BigDecimal total = Money.sum(percents);
        List<BigDecimal> result = new ArrayList<>(n);
        if (total.signum() <= 0) {
            BigDecimal even = BigDecimal.valueOf(100 / n);
            for (int i = 0; i < n; i++) {
                result.add(i == 0 ? Money.HUNDRED.subtract(even.multiply(BigDecimal.valueOf(n - 1))) : even);
            }
            return result;
        }
        for (BigDecimal p : percents) {
            result.add(p.multiply(Money.HUNDRED).divide(total, 0, RoundingMode.HALF_UP));
        }
        BigDecimal diff = Money.HUNDRED.subtract(Money.sum(result));
        int largest = 0;
        for (int i = 1; i < n; i++) {
            if (result.get(i).compareTo(result.get(largest)) > 0) {
                largest = i;
            }
        }
        result.set(largest, result.get(largest).add(diff));
        return result;
    }

    @Transactional(readOnly = true)
    public List<RulePreset> listPresets() {
        return presets.findAllByOrderBySortOrderAsc();
    }

    /** Cria os potes de uma regra para o usuário (não mexe nas categorias). */
    @Transactional
    public List<BucketWithClaims> createBucketsFromPreset(User user, String presetId) {
        RulePreset preset = presets.findWithBucketsById(presetId).orElseThrow(NotFoundException::new);
        List<BucketWithClaims> created = new ArrayList<>();
        int order = 0;
        for (RulePreset.PresetBucket pb : preset.getBuckets()) {
            Bucket bucket = buckets.save(new Bucket(user, order++, pb.getName(), pb.getPercent(), pb.getKind(),
                    pb.getDescription()));
            created.add(new BucketWithClaims(bucket, pb.claims()));
        }
        return created;
    }

    /** Aplica uma regra pronta: troca os potes e remapeia cada categoria para o pote mais adequado. */
    @Transactional
    public void applyPreset(Long userId, String presetId) {
        User user = users.getReferenceById(userId);
        List<Bucket> old = buckets.findByUserIdOrderBySortOrderAscIdAsc(userId);
        List<BucketWithClaims> created = createBucketsFromPreset(user, presetId);
        for (Category category : categories.findByUserIdOrderByIdAsc(userId)) {
            category.setBucket(bucketFor(category.getSystemKey(), category.getKind(), created));
        }
        categories.flush();
        buckets.deleteAll(old);
        settingsOf(userId).setPresetId(presetId);
    }

    /** Edita um pote. Se o tipo mudar, as categorias dele passam a ter o novo tipo. */
    @Transactional
    public void updateBucket(Long userId, Long bucketId, String name, BigDecimal percent, BucketKind kind,
            String description) {
        Bucket bucket = buckets.findByIdAndUserId(bucketId, userId).orElseThrow(NotFoundException::new);
        if (bucket.getKind() != kind) {
            for (Category category : categories.findByBucket(bucket)) {
                category.setKind(kind);
            }
        }
        bucket.setName(name);
        bucket.setPercent(percent);
        bucket.setKind(kind);
        bucket.setDescription(description);
        settingsOf(userId).setPresetId(UserSettings.CUSTOM_PRESET);
    }

    @Transactional
    public void addBucket(Long userId) {
        List<Bucket> current = buckets.findByUserIdOrderBySortOrderAscIdAsc(userId);
        if (current.size() >= MAX_BUCKETS) {
            throw new BusinessRuleException("A regra pode ter no máximo " + MAX_BUCKETS + " potes.");
        }
        int nextOrder = current.isEmpty() ? 0 : current.getLast().getSortOrder() + 1;
        buckets.save(new Bucket(users.getReferenceById(userId), nextOrder, "Novo pote", BigDecimal.ZERO,
                BucketKind.OTHER, null));
        settingsOf(userId).setPresetId(UserSettings.CUSTOM_PRESET);
    }

    /** Remove um pote. As categorias dele vão para o primeiro pote que sobrar. */
    @Transactional
    public void removeBucket(Long userId, Long bucketId) {
        Bucket bucket = buckets.findByIdAndUserId(bucketId, userId).orElseThrow(NotFoundException::new);
        List<Bucket> remaining = new ArrayList<>(buckets.findByUserIdOrderBySortOrderAscIdAsc(userId));
        remaining.removeIf(b -> b.getId().equals(bucketId));
        if (remaining.isEmpty()) {
            throw new BusinessRuleException("A regra precisa de pelo menos um pote.");
        }
        Bucket fallback = remaining.getFirst();
        for (Category category : categories.findByBucket(bucket)) {
            category.setBucket(fallback);
            category.setKind(fallback.getKind());
        }
        categories.flush();
        buckets.delete(bucket);
        settingsOf(userId).setPresetId(UserSettings.CUSTOM_PRESET);
    }

    @Transactional
    public void normalizeBuckets(Long userId) {
        List<Bucket> current = buckets.findByUserIdOrderBySortOrderAscIdAsc(userId);
        List<BigDecimal> normalized = normalizePercents(current.stream().map(Bucket::getPercent).toList());
        for (int i = 0; i < current.size(); i++) {
            current.get(i).setPercent(normalized.get(i));
        }
        settingsOf(userId).setPresetId(UserSettings.CUSTOM_PRESET);
    }

    private UserSettings settingsOf(Long userId) {
        return settings.findById(userId).orElseThrow(NotFoundException::new);
    }
}
