package io.github.fabasdev.financas.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Resultado do questionário de perfil. As respostas ficam só no navegador. */
@Entity
@Table(name = "investor_profiles")
public class InvestorProfile {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskProfile profile;

    @Column(nullable = false)
    private int score;

    /** O perfil deve ser refeito a cada 2 anos. */
    @Column(name = "taken_on", nullable = false)
    private LocalDate takenOn;

    /** Percentuais por classe ajustados pelo usuário. Os que faltam vêm do perfil. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "investor_profile_allocations", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "asset_class", length = 30)
    @Column(name = "percent", nullable = false, precision = 5, scale = 2)
    private Map<AssetClass, BigDecimal> customAllocation = new HashMap<>();

    protected InvestorProfile() {
    }

    public InvestorProfile(User user, RiskProfile profile, int score, LocalDate takenOn) {
        this.user = user;
        this.profile = profile;
        this.score = score;
        this.takenOn = takenOn;
    }

    public Long getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public RiskProfile getProfile() {
        return profile;
    }

    public void setProfile(RiskProfile profile) {
        this.profile = profile;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public LocalDate getTakenOn() {
        return takenOn;
    }

    public void setTakenOn(LocalDate takenOn) {
        this.takenOn = takenOn;
    }

    public Map<AssetClass, BigDecimal> getCustomAllocation() {
        return customAllocation;
    }

    /** Cópia ordenada, para não expor a coleção gerenciada pelo Hibernate. */
    public Map<AssetClass, BigDecimal> customAllocationCopy() {
        return customAllocation.isEmpty() ? new EnumMap<>(AssetClass.class) : new EnumMap<>(customAllocation);
    }
}
