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
import java.time.YearMonth;

@Entity
@Table(name = "goals")
public class Goal {

    /** Chaves das metas criadas no cadastro. */
    public static final String RESERVE_KEY = "reserve";
    public static final String INVESTMENTS_KEY = "investments";

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalKind kind;

    /** 0 = sem valor-alvo. Ignorado na reserva, cujo alvo é calculado. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal target = BigDecimal.ZERO;

    private YearMonth deadline;

    /** Saldo que já existia antes dos lançamentos registrados no app. */
    @Column(name = "initial_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal initialBalance = BigDecimal.ZERO;

    protected Goal() {
    }

    public Goal(User user, String systemKey, String name, String icon, GoalKind kind) {
        this.user = user;
        this.systemKey = systemKey;
        this.name = name;
        this.icon = icon;
        this.kind = kind;
    }

    /** A reserva padrão não pode ser apagada. */
    public boolean isReserve() {
        return RESERVE_KEY.equals(systemKey);
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

    public GoalKind getKind() {
        return kind;
    }

    public void setKind(GoalKind kind) {
        this.kind = kind;
    }

    public BigDecimal getTarget() {
        return target;
    }

    public void setTarget(BigDecimal target) {
        this.target = target;
    }

    public YearMonth getDeadline() {
        return deadline;
    }

    public void setDeadline(YearMonth deadline) {
        this.deadline = deadline;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }
}
