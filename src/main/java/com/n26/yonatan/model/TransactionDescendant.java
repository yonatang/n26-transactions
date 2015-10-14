package com.n26.yonatan.model;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

/**
 * This entity maps for each entity all their descendants (direct and indirect)
 * for quicker summing
 */
@Entity
@Data
public class TransactionDescendant {

    @Id
    @GeneratedValue
    private long id;

    @ManyToOne(optional = false)
    private TransactionEntity parent;

    @ManyToOne(optional = false)
    private TransactionEntity descendant;
}
