package io.github.fabasdev.financas.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Regra pronta de distribuição da renda (50/30/20, 6 potes...). Só leitura: vem da migração V2. */
@Entity
@Table(name = "rule_presets")
public class RulePreset {

    @Id
    @Column(length = 20)
    private String id;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "short_name", nullable = false, length = 20)
    private String shortName;

    @Column(nullable = false, length = 300)
    private String description;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "rule_preset_buckets", joinColumns = @JoinColumn(name = "preset_id"))
    @OrderColumn(name = "sort_order")
    private List<PresetBucket> buckets = new ArrayList<>();

    protected RulePreset() {
    }

    public String getId() {
        return id;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getDescription() {
        return description;
    }

    public List<PresetBucket> getBuckets() {
        return List.copyOf(buckets);
    }

    @Embeddable
    public static class PresetBucket {

        @Column(nullable = false, length = 60)
        private String name;

        @Column(nullable = false, precision = 5, scale = 2)
        private BigDecimal percent;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private BucketKind kind;

        @Column(nullable = false, length = 500)
        private String description;

        @Column(name = "claimed_categories", nullable = false, length = 200)
        private String claimedCategories;

        protected PresetBucket() {
        }

        public String getName() {
            return name;
        }

        public BigDecimal getPercent() {
            return percent;
        }

        public BucketKind getKind() {
            return kind;
        }

        public String getDescription() {
            return description;
        }

        /** Chaves das categorias padrão que este pote puxa para si. */
        public Set<String> claims() {
            return Arrays.stream(claimedCategories.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
