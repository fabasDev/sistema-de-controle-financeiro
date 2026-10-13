package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.InvestorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/** O id do perfil é o próprio id do usuário. */
public interface InvestorProfileRepository extends JpaRepository<InvestorProfile, Long> {
}
