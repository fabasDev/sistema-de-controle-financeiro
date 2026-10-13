package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/** O id das configurações é o próprio id do usuário. */
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
}
