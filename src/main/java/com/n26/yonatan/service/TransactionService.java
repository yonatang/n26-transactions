package com.n26.yonatan.service;

import com.google.common.base.Preconditions;
import com.n26.yonatan.dto.Sum;
import com.n26.yonatan.dto.Transaction;
import com.n26.yonatan.exception.BadRequestException;
import com.n26.yonatan.exception.ConflictException;
import com.n26.yonatan.exception.NotFoundException;
import com.n26.yonatan.model.TransactionEntity;
import com.n26.yonatan.repository.Db;
import com.n26.yonatan.repository.TypeIdxDb;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import static java.util.Collections.emptySet;

/**
 * Created by yonatan on 12/10/2015.
 */
@Service
@Slf4j
public class TransactionService {

    @Autowired
    private Db db;

    @Autowired
    private TypeIdxDb typeIdxDb;

    /**
     * Create a transaction in the DB with id transactionId.
     * Throw an exception if transaction already exists
     *
     * @param transactionId
     * @param t
     */
    public void createTransaction(long transactionId, Transaction t) {
        log.trace("createTransaction {} {}", transactionId, t);
        Preconditions.checkNotNull(t, "TransactionEntity must not be null");

        if (t.getParentId() != null && !db.containsKey(t.getParentId())) {
            log.debug("When saving transaction {}, could not found parent", t);
            throw new BadRequestException("parent not found");
        }
        Set<Long> typeIdx = typeIdxDb.computeIfAbsent(t.getType(), s -> new ConcurrentSkipListSet<>());

        // each entity that is created has a unique UUID
        final TransactionEntity entity = new TransactionEntity(transactionId, t);
        log.debug("Saving a transaction {}", entity);
        // atomic creation
        TransactionEntity dbEntity = db.computeIfAbsent(transactionId, id -> entity);
        // If it is already exists in the db, it would have a different UUID
        if (!dbEntity.equals(entity)) {
            //The dbEntity was created earlier, and therefore you should fail this one
            throw new ConflictException("conflict");
        }
        typeIdx.add(entity.getId());

        // since transactions are immutable, sums can be easily pre-calculated.
        // travel on the tree, and update the parents' sum with the amount that was
        // added.
        TransactionEntity parent = entity;
        while (parent.getParentId() != null) {
            parent = db.get(parent.getParentId());
            log.debug("Updating sum: adding {} for {}", t.getAmount(), parent);
            //atomic increment
            parent.getSum().addAndGet(t.getAmount());
        }
    }

    /**
     * Find a transaction with the transactionId.
     * Throws an exception if not found
     *
     * @param transactionId
     * @return
     */
    public Transaction findTransaction(long transactionId) {
        log.trace("findTransaction {}", transactionId);
        TransactionEntity entity = db.get(transactionId);
        if (entity == null) {
            throw new NotFoundException("not found");
        }

        Transaction transaction = new Transaction();
        transaction.setAmount(entity.getAmount());
        transaction.setType(entity.getType());
        transaction.setParentId(entity.getParentId());
        return transaction;
    }

    public Set<Long> getTransactionIdsByType(String type) {
        log.trace("getTransactionIdsByType {}", type);
        Preconditions.checkNotNull(type, "Type must not be null");
        // new HashSet() might be harmful, as it will generate unnessecery object
        // that would need to be GC'ed later on.
        Set<Long> ids = typeIdxDb.getOrDefault(type, emptySet());
        return ids;
    }

    /**
     * Calculates the sum of the transaction and all its children
     *
     * @param transactionId
     * @return
     */
    public Sum sumTransactions(long transactionId) {
        log.trace("sumTransactions {}", transactionId);

        TransactionEntity t = db.get(transactionId);
        if (t == null) {
            throw new NotFoundException("not found");
        }
        double sum = t.getSum().get();
        log.debug("Found sum {} for transaction {}", sum, t);

        return new Sum(sum);
    }
}
