package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.AssetClass;
import io.github.fabasdev.financas.domain.InvestorProfile;
import io.github.fabasdev.financas.domain.RiskProfile;
import io.github.fabasdev.financas.dto.PlanItem;
import io.github.fabasdev.financas.repository.InvestorProfileRepository;
import io.github.fabasdev.financas.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil de investidor e carteira sugerida. O questionário roda no navegador e manda só o resultado;
 * a divisão do dinheiro entre as classes é calculada aqui, em BigDecimal.
 */
@Service
public class ProfileService {

    /** Pontuação máxima do questionário: 4+4+4+3+3+2+3+2, as maiores opções de cada pergunta em advisor.ts. */
    public static final int MAX_SCORE = 25;

    /** Perfil com a carteira já resolvida (padrão do perfil + ajustes do usuário). */
    public record ProfileView(RiskProfile profile, int score, LocalDate takenOn, boolean expired,
            Map<AssetClass, BigDecimal> allocation, Map<AssetClass, BigDecimal> custom) {
    }

    private final InvestorProfileRepository profiles;
    private final UserRepository users;
    private final PortfolioService portfolio;
    private final Clock clock;

    public ProfileService(InvestorProfileRepository profiles, UserRepository users, PortfolioService portfolio,
            Clock clock) {
        this.profiles = profiles;
        this.users = users;
        this.portfolio = portfolio;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<ProfileView> find(Long userId) {
        return profiles.findById(userId).map(p -> {
            Map<AssetClass, BigDecimal> custom = p.customAllocationCopy();
            boolean expired = ChronoUnit.MONTHS.between(p.getTakenOn().withDayOfMonth(1),
                    LocalDate.now(clock).withDayOfMonth(1)) >= 24;
            return new ProfileView(p.getProfile(), p.getScore(), p.getTakenOn(), expired,
                    portfolio.allocationFor(p.getProfile(), custom), custom);
        });
    }

    /** Guarda o resultado do questionário. Refazer o questionário apaga os ajustes manuais da carteira. */
    @Transactional
    public void saveResult(Long userId, RiskProfile profile, int score) {
        if (score < 0 || score > MAX_SCORE) {
            throw new InvalidInputException("score", "Pontuação inválida.");
        }
        InvestorProfile p = profiles.findById(userId)
                .orElseGet(() -> new InvestorProfile(users.getReferenceById(userId), profile, score, LocalDate.now(clock)));
        p.setProfile(profile);
        p.setScore(score);
        p.setTakenOn(LocalDate.now(clock));
        p.getCustomAllocation().clear();
        profiles.save(p);
    }

    /** Ajustes da carteira por classe. Vazio volta ao padrão do perfil. */
    @Transactional
    public void saveCustomAllocation(Long userId, Map<AssetClass, BigDecimal> custom) {
        InvestorProfile p = profiles.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("Faça o questionário de perfil antes de ajustar a carteira."));
        p.getCustomAllocation().clear();
        p.getCustomAllocation().putAll(new EnumMap<>(custom));
    }

    @Transactional
    public void delete(Long userId) {
        profiles.findById(userId).ifPresent(profiles::delete);
    }

    /** Divide o valor do mês pela carteira do perfil. */
    public List<PlanItem> plan(ProfileView profile, BigDecimal toInvest) {
        return portfolio.split(toInvest, profile.allocation());
    }
}
