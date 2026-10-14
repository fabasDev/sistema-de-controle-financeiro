package io.github.fabasdev.financas.service.undo;

import io.github.fabasdev.financas.domain.Bucket;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.Transaction;
import java.util.Set;

/** Copia o estado de uma entidade antes de apagá-la. */
public final class Snapshots {

    private Snapshots() {
    }

    public static UndoAction.TxSnapshot of(Transaction t) {
        return new UndoAction.TxSnapshot(t.getType(), t.getDate(), t.getDescription(), t.getAmount(),
                id(t.getCategory()), id(t.getGoal()), t.getRecurring() == null ? null : t.getRecurring().getId(),
                t.getRecurringMonth(), t.isPaid());
    }

    public static UndoAction.RecurringSnapshot of(RecurringEntry r) {
        return new UndoAction.RecurringSnapshot(r.getType(), r.getDescription(), r.getAmount(), id(r.getCategory()),
                id(r.getGoal()), r.getDayOfMonth(), r.getStartMonth(), r.getEndMonth(), r.isActive(),
                Set.copyOf(r.getSkippedMonths()));
    }

    public static UndoAction.GoalSnapshot of(Goal g) {
        return new UndoAction.GoalSnapshot(g.getName(), g.getIcon(), g.getKind(), g.getTarget(), g.getDeadline(),
                g.getInitialBalance());
    }

    public static UndoAction.CategorySnapshot of(Category c) {
        return new UndoAction.CategorySnapshot(c.getSystemKey(), c.getName(), c.getIcon(), c.getBucket().getId(),
                c.getKind(), c.getBudget(), id(c.getGoal()));
    }

    public static UndoAction.BucketSnapshot of(Bucket b) {
        return new UndoAction.BucketSnapshot(b.getSortOrder(), b.getName(), b.getPercent(), b.getKind(),
                b.getDescription());
    }

    private static Long id(Category c) {
        return c == null ? null : c.getId();
    }

    private static Long id(Goal g) {
        return g == null ? null : g.getId();
    }
}
