package io.github.fabasdev.financas.service.undo;

import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.GoalKind;
import io.github.fabasdev.financas.domain.TransactionType;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * O que é preciso para desfazer uma exclusão: uma cópia do registro apagado e das ligações que
 * ele tinha. Fica na sessão do usuário por alguns minutos (ver UndoStore).
 *
 * <p>O plano falava em "exclusão adiada" (marcar como apagado e apagar depois). A escolha foi apagar
 * de verdade e recriar no desfazer: assim nenhuma soma, saldo ou listagem precisa lembrar de
 * ignorar registros "meio apagados", e um esquecimento não distorce os números.
 */
public sealed interface UndoAction extends Serializable {

    record TxSnapshot(TransactionType type, LocalDate date, String description, BigDecimal amount, Long categoryId,
            Long goalId, Long recurringId, YearMonth recurringMonth, boolean paid) implements Serializable {
    }

    record RecurringSnapshot(TransactionType type, String description, BigDecimal amount, Long categoryId,
            Long goalId, int dayOfMonth, YearMonth startMonth, YearMonth endMonth, boolean active,
            Set<YearMonth> skippedMonths) implements Serializable {
    }

    record GoalSnapshot(String name, String icon, GoalKind kind, BigDecimal target, YearMonth deadline,
            BigDecimal initialBalance) implements Serializable {
    }

    record CategorySnapshot(String systemKey, String name, String icon, Long bucketId, BucketKind kind,
            BigDecimal budget, Long goalId) implements Serializable {
    }

    record BucketSnapshot(int sortOrder, String name, BigDecimal percent, BucketKind kind, String description)
            implements Serializable {
    }

    /** Lançamento que estava ligado a uma conta fixa, e o mês dessa ligação. */
    record RecurringLink(Long transactionId, YearMonth month) implements Serializable {
    }

    record TransactionDeleted(TxSnapshot transaction) implements UndoAction {
    }

    /**
     * @param deletedFuture lançamentos futuros não pagos que foram apagados junto com a conta
     * @param unlinked      lançamentos que continuaram no histórico, mas perderam a ligação
     */
    record RecurringDeleted(RecurringSnapshot recurring, List<TxSnapshot> deletedFuture,
            List<RecurringLink> unlinked) implements UndoAction {
    }

    /** As chaves estrangeiras soltam as ligações (SET NULL); os ids são guardados antes. */
    record GoalDeleted(GoalSnapshot goal, List<Long> transactionIds, List<Long> recurringIds,
            List<Long> categoryIds) implements UndoAction {
    }

    record CategoryDeleted(CategorySnapshot category) implements UndoAction {
    }

    record CategoryArchived(Long categoryId) implements UndoAction {
    }

    /** @param movedCategories categorias que foram para outro pote, com a natureza que tinham antes */
    record BucketRemoved(BucketSnapshot bucket, String previousPresetId, Map<Long, BucketKind> movedCategories)
            implements UndoAction {
    }

    /**
     * Troca de regra pronta.
     *
     * @param categoryBucketIndex categoria → posição do pote antigo em {@code previousBuckets}
     * @param createdBucketIds    potes criados pela regra nova (apagados no desfazer)
     */
    record PresetApplied(String previousPresetId, List<BucketSnapshot> previousBuckets,
            Map<Long, Integer> categoryBucketIndex, List<Long> createdBucketIds) implements UndoAction {
    }
}
