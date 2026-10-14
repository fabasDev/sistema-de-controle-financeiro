package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.dto.Simulation;
import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Preferências: nome de exibição, valores salvos do simulador e "recomeçar do zero". */
@Service
public class SettingsService {

    private final UserSettingsRepository settings;
    private final UserRepository users;
    private final UserDataEraser eraser;
    private final UserDataProvisioner provisioner;

    public SettingsService(UserSettingsRepository settings, UserRepository users, UserDataEraser eraser,
            UserDataProvisioner provisioner) {
        this.settings = settings;
        this.users = users;
        this.eraser = eraser;
        this.provisioner = provisioner;
    }

    @Transactional(readOnly = true)
    public UserSettings get(Long userId) {
        return settings.findById(userId).orElseThrow(NotFoundException::new);
    }

    @Transactional
    public void updateDisplayName(Long userId, String displayName) {
        get(userId).setDisplayName(displayName == null ? "" : displayName.strip());
    }

    @Transactional
    public void saveSimulation(Long userId, Simulation.Params p, BigDecimal target) {
        UserSettings s = get(userId);
        s.setSimInitial(p.initial());
        s.setSimMonthly(p.monthly());
        s.setSimAnnualRate(p.annualRate());
        s.setSimYears(p.years());
        s.setSimInflation(p.inflation());
        s.setSimYearlyIncrease(p.yearlyIncrease());
        s.setSimTarget(target);
    }

    /** Apaga lançamentos, contas, metas e regras e volta aos dados iniciais. A conta continua. */
    @Transactional
    public void resetData(Long userId) {
        eraser.eraseFinancialData(userId);
        settings.deleteAllByUserId(userId);
        settings.flush();
        provisioner.provision(users.getReferenceById(userId));
    }
}
