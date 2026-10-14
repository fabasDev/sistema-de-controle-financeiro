package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** O id das configurações é o próprio id do usuário. */
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    /** Apaga todos os registros do usuário (usado ao excluir a conta e ao importar um backup). */
    @Modifying
    @Query("delete from UserSettings e where e.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
