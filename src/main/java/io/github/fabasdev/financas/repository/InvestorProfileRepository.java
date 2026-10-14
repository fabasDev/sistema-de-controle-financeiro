package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.InvestorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** O id do perfil é o próprio id do usuário. */
public interface InvestorProfileRepository extends JpaRepository<InvestorProfile, Long> {

    /** Apaga todos os registros do usuário (usado ao excluir a conta e ao importar um backup). */
    @Modifying
    @Query("delete from InvestorProfile e where e.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
