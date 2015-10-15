package com.n26.yonatan.repository;

import com.n26.yonatan.model.TransactionDescendant;
import com.n26.yonatan.model.TransactionEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Transaction Descendant repository
 */
public interface TransactionDescendantRepository extends CrudRepository<TransactionDescendant, Long> {

    @Query("SELECT d.descendant.amount FROM TransactionDescendant d where d.parent = :transaction")
    List<Double> amountsByParent(@Param("transaction") TransactionEntity transactionEntity);
}
