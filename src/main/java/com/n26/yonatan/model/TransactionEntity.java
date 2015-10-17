package com.n26.yonatan.model;

import com.google.common.util.concurrent.AtomicDouble;
import com.n26.yonatan.dto.Transaction;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Transaction DB Entity.
 */
@Data
@EqualsAndHashCode(exclude = "sum")
@RequiredArgsConstructor
public class TransactionEntity {

    public TransactionEntity(long id, Transaction t) {
        this.id = id;
        this.amount = t.getAmount();
        this.type = t.getType();
        this.parentId = t.getParentId();
        this.sum.addAndGet(this.amount);
    }

    private final long id;

    private final UUID uuid = UUID.randomUUID();

    private final Long parentId;

    private final String type;

    private final double amount;

    private final AtomicDouble sum = new AtomicDouble();
}
