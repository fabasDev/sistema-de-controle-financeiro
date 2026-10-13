package io.github.fabasdev.financas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "categories")
public class Category {

    /** Recebe os gastos sem categoria no resumo do mês. */
    public static final String OTHER_KEY = "other";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "system_key", length = 30)
    private String systemKey;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 40)
    private String icon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bucket_id", nullable = false)
    private Bucket bucket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BucketKind kind;

    /** Limite mensal opcional. */
    @Column(precision = 14, scale = 2)
    private BigDecimal budget;

    /** Meta que recebe automaticamente os lançamentos desta categoria. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Column(nullable = false)
    private boolean archived;

    protected Category() {
    }

    public Category(User user, String systemKey, String name, String icon, Bucket bucket, BucketKind kind) {
        this.user = user;
        this.systemKey = systemKey;
        this.name = name;
        this.icon = icon;
        this.bucket = bucket;
        this.kind = kind;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getSystemKey() {
        return systemKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public void setBucket(Bucket bucket) {
        this.bucket = bucket;
    }

    public BucketKind getKind() {
        return kind;
    }

    public void setKind(BucketKind kind) {
        this.kind = kind;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public Goal getGoal() {
        return goal;
    }

    public void setGoal(Goal goal) {
        this.goal = goal;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }
}
