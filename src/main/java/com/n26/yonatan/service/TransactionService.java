package com.n26.yonatan.service;

import com.google.common.base.Preconditions;
import com.n26.yonatan.dto.Sum;
import com.n26.yonatan.dto.Transaction;
import com.n26.yonatan.exception.BadRequestException;
import com.n26.yonatan.exception.NotFoundException;
import com.n26.yonatan.exception.ServerErrorException;
import com.n26.yonatan.model.TransactionDescendant;
import com.n26.yonatan.model.TransactionEntity;
import com.n26.yonatan.repository.TransactionDescendantRepository;
import com.n26.yonatan.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.summingDouble;

/**
 * Created by yonatan on 12/10/2015.
 */
@Service
@Slf4j
public class TransactionService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionDescendantRepository transactionDescendantRepository;

    /**
     * Create a transaction in the DB with id transactionId.
     * Throw an exception if transaction already exists
     *
     * @param transactionId
     * @param t
     */
    @Transactional
    public void createTransaction(long transactionId, Transaction t) {
        log.trace("createTransaction {} {}", transactionId, t);
        Preconditions.checkNotNull(t, "TransactionEntity must not be null");

        TransactionEntity entity = new TransactionEntity();
        entity.setId(transactionId);
        entity.setAmount(t.getAmount());
        entity.setType(t.getType());
        if (t.getParentId() != null) {
            // if a parent was added, verify it exists and add it to the entity
            TransactionEntity parent = transactionRepository.findOne(t.getParentId());
            if (parent == null) {
                throw new BadRequestException("parent not found");
            }
            entity.setParent(parent);
        }

        entity = transactionRepository.save(entity);
        Set<Long> visited = new HashSet<>();
        TransactionEntity parent = entity.getParent();
        while (parent != null) {
            // cycles are not possible via the API, but if someone will mess with the underlying DB
            // it might happen. In such case, this would never end - causing thread leakage.
            // Therefore, cycle detection is a good practice.
            if (visited.contains(parent.getId())) {
                log.error("Cyclic transaction detected when adding transaction id {}: {}", transactionId, visited);
                throw new ServerErrorException("cyclic transaction");
            }
            visited.add(parent.getId());

            TransactionDescendant descendant = new TransactionDescendant();
            descendant.setParent(parent);
            descendant.setDescendant(entity);
            transactionDescendantRepository.save(descendant);
            parent = parent.getParent();
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
        TransactionEntity entity = transactionRepository.findOne(transactionId);
        if (entity == null) {
            throw new NotFoundException("not found");
        }

        Transaction transaction = new Transaction();
        transaction.setAmount(entity.getAmount());
        transaction.setType(entity.getType());
        if (entity.getParent() != null) {
            transaction.setParentId(entity.getParent().getId());
        }
        return transaction;
    }

    public List<Long> getTransactionIdsByType(String type) {
        log.trace("getTransactionIdsByType {}", type);
        Preconditions.checkNotNull(type, "Type must not be null");
        return transactionRepository.getTransactionIdsByType(type);
    }

    /**
     * Calculates the sum of the transaction and all its children. If a cycle is detected, an exception is thrown
     *
     * @param transactionId
     * @return
     */
    public Sum sumTransactions(long transactionId) {
        log.trace("sumTransactions {}", transactionId);
        TransactionEntity t = transactionRepository.findOne(transactionId);
        if (t == null) {
            throw new NotFoundException("not found");
        }
        List<TransactionDescendant> descendants = transactionDescendantRepository.findByParent(t);

        double sum = t.getAmount() +
                // calculate the sum of the amounts of the descendants
                descendants.stream()
                        .map(TransactionDescendant::getDescendant)
                        .collect(summingDouble(TransactionEntity::getAmount));
        return new Sum(sum);
    }
}
