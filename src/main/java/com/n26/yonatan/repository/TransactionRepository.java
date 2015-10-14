package com.n26.yonatan.repository;

import com.n26.yonatan.model.TransactionEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Transactions Repository
 */
public interface TransactionRepository extends CrudRepository<TransactionEntity, Long> {

    /**
     * Returns list of transaction ids that matches the type.
     * Projection is used to reduce DB traffic
     *
     * @param type
     * @return
     */
    @Query("SELECT t.id FROM TransactionEntity t WHERE t.type = :type")
    List<Long> getTransactionIdsByType(@Param("type") String type);

}
