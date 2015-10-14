package com.n26.yonatan.repository;

import com.n26.yonatan.model.TransactionDescendant;
import com.n26.yonatan.model.TransactionEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Transaction Descendant repository
 */
public interface TransactionDescendantRepository extends CrudRepository<TransactionDescendant, Long> {
    List<TransactionDescendant> findByParent(TransactionEntity transactionEntity);
}
