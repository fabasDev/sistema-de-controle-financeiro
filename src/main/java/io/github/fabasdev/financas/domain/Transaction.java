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
import java.time.LocalDate;
import java.time.YearMonth;

/** Um lançamento: receita ou despesa. O mês vem sempre da data. */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_id")
    private RecurringEntry recurring;

    /** Mês da conta fixa a que este lançamento pertence (mesmo se a data for editada). */
    @Column(name = "recurring_month")
    private YearMonth recurringMonth;

    /** Pago (despesa) ou recebido (receita). */
    @Column(nullable = false)
    private boolean paid;

    protected Transaction() {
    }

    public Transaction(User user, TransactionType type, LocalDate date, String description, BigDecimal amount,
            boolean paid) {
        this.user = user;
        this.type = type;
        this.date = date;
        this.description = description;
        this.amount = amount;
        this.paid = paid;
    }

    public YearMonth getMonth() {
        return YearMonth.from(date);
    }

    public void linkRecurring(RecurringEntry recurring, YearMonth month) {
        this.recurring = recurring;
        this.recurringMonth = month;
    }

    public void unlinkRecurring() {
        this.recurring = null;
        this.recurringMonth = null;
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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
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

    public RecurringEntry getRecurring() {
        return recurring;
    }

    public YearMonth getRecurringMonth() {
        return recurringMonth;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }
}
