package io.github.fabasdev.financas.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

/** Conta fixa ou parcelamento: gera um lançamento por mês entre startMonth e endMonth. */
@Entity
@Table(name = "recurring_entries")
public class RecurringEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    /** Dia do mês (1 a 31). Em meses mais curtos, vira o último dia. */
    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "start_month", nullable = false)
    private YearMonth startMonth;

    /** Último mês, inclusive. Vazio = sem fim. */
    @Column(name = "end_month")
    private YearMonth endMonth;

    @Column(nullable = false)
    private boolean active = true;

    /** Meses em que o lançamento foi apagado à mão e não deve ser recriado. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recurring_skips", joinColumns = @JoinColumn(name = "recurring_id"))
    @Column(name = "skipped_month", nullable = false)
    private Set<YearMonth> skippedMonths = new HashSet<>();

    protected RecurringEntry() {
    }

    public RecurringEntry(User user, TransactionType type, String description, BigDecimal amount,
            int dayOfMonth, YearMonth startMonth) {
        this.user = user;
        this.type = type;
        this.description = description;
        this.amount = amount;
        this.dayOfMonth = dayOfMonth;
        this.startMonth = startMonth;
    }

    /** Se a conta gera lançamento no mês (ativa e dentro do intervalo). */
    public boolean coversMonth(YearMonth month) {
        return active && !month.isBefore(startMonth) && (endMonth == null || !month.isAfter(endMonth));
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Goal getGoal() {
        return goal;
    }

    public void setGoal(Goal goal) {
        this.goal = goal;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(int dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public YearMonth getStartMonth() {
        return startMonth;
    }

    public void setStartMonth(YearMonth startMonth) {
        this.startMonth = startMonth;
    }

    public YearMonth getEndMonth() {
        return endMonth;
    }

    public void setEndMonth(YearMonth endMonth) {
        this.endMonth = endMonth;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<YearMonth> getSkippedMonths() {
        return skippedMonths;
    }
}
