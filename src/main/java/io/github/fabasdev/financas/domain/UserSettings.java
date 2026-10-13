package io.github.fabasdev.financas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Preferências do usuário: regra escolhida, reserva e os últimos valores do simulador. */
@Entity
@Table(name = "user_settings")
public class UserSettings {

    public static final String CUSTOM_PRESET = "custom";

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName = "";

    @Column(name = "preset_id", nullable = false, length = 20)
    private String presetId;

    @Column(name = "reserve_months", nullable = false)
    private int reserveMonths = 6;

    @Enumerated(EnumType.STRING)
    @Column(name = "reserve_base", nullable = false, length = 20)
    private ReserveBase reserveBase = ReserveBase.NEEDS;

    @Column(name = "reserve_manual_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal reserveManualCost = BigDecimal.ZERO;

    /** Percentual (0 a 100) do "Futuro" que vai para a reserva enquanto ela não estiver completa. */
    @Column(name = "reserve_priority", nullable = false, precision = 5, scale = 2)
    private BigDecimal reservePriority = new BigDecimal("70");

    @Column(name = "sim_initial", nullable = false, precision = 14, scale = 2)
    private BigDecimal simInitial = BigDecimal.ZERO;

    @Column(name = "sim_monthly", nullable = false, precision = 14, scale = 2)
    private BigDecimal simMonthly = new BigDecimal("500");

    @Column(name = "sim_annual_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal simAnnualRate = new BigDecimal("10");

    @Column(name = "sim_years", nullable = false)
    private int simYears = 10;

    @Column(name = "sim_inflation", nullable = false, precision = 7, scale = 4)
    private BigDecimal simInflation = new BigDecimal("4.5");

    @Column(name = "sim_yearly_increase", nullable = false, precision = 7, scale = 4)
    private BigDecimal simYearlyIncrease = BigDecimal.ZERO;

    @Column(name = "sim_target", nullable = false, precision = 14, scale = 2)
    private BigDecimal simTarget = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean onboarded;

    protected UserSettings() {
    }

    public UserSettings(User user, String presetId) {
        this.user = user;
        this.presetId = presetId;
    }

    public Long getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPresetId() {
        return presetId;
    }

    public void setPresetId(String presetId) {
        this.presetId = presetId;
    }

    public int getReserveMonths() {
        return reserveMonths;
    }

    public void setReserveMonths(int reserveMonths) {
        this.reserveMonths = reserveMonths;
    }

    public ReserveBase getReserveBase() {
        return reserveBase;
    }

    public void setReserveBase(ReserveBase reserveBase) {
        this.reserveBase = reserveBase;
    }

    public BigDecimal getReserveManualCost() {
        return reserveManualCost;
    }

    public void setReserveManualCost(BigDecimal reserveManualCost) {
        this.reserveManualCost = reserveManualCost;
    }

    public BigDecimal getReservePriority() {
        return reservePriority;
    }

    public void setReservePriority(BigDecimal reservePriority) {
        this.reservePriority = reservePriority;
    }

    public BigDecimal getSimInitial() {
        return simInitial;
    }

    public void setSimInitial(BigDecimal simInitial) {
        this.simInitial = simInitial;
    }

    public BigDecimal getSimMonthly() {
        return simMonthly;
    }

    public void setSimMonthly(BigDecimal simMonthly) {
        this.simMonthly = simMonthly;
    }

    public BigDecimal getSimAnnualRate() {
        return simAnnualRate;
    }

    public void setSimAnnualRate(BigDecimal simAnnualRate) {
        this.simAnnualRate = simAnnualRate;
    }

    public int getSimYears() {
        return simYears;
    }

    public void setSimYears(int simYears) {
        this.simYears = simYears;
    }

    public BigDecimal getSimInflation() {
        return simInflation;
    }

    public void setSimInflation(BigDecimal simInflation) {
        this.simInflation = simInflation;
    }

    public BigDecimal getSimYearlyIncrease() {
        return simYearlyIncrease;
    }

    public void setSimYearlyIncrease(BigDecimal simYearlyIncrease) {
        this.simYearlyIncrease = simYearlyIncrease;
    }

    public BigDecimal getSimTarget() {
        return simTarget;
    }

    public void setSimTarget(BigDecimal simTarget) {
        this.simTarget = simTarget;
    }

    public boolean isOnboarded() {
        return onboarded;
    }

    public void setOnboarded(boolean onboarded) {
        this.onboarded = onboarded;
    }
}
