package com.n26.yonatan.testutils;

import com.n26.yonatan.dto.Transaction;
import com.n26.yonatan.model.TransactionDescendant;
import com.n26.yonatan.model.TransactionEntity;
import org.apache.commons.lang3.RandomUtils;

/**
 * Created by yonatan on 15/10/2015.
 */
public class Utils {
    public static TransactionDescendant descendant(long id, TransactionEntity parent, TransactionEntity descendant) {
        TransactionDescendant td = new TransactionDescendant();
        td.setDescendant(descendant);
        td.setParent(parent);
        td.setId(id);
        return td;
    }

    public static TransactionDescendant descendant(TransactionEntity parent, TransactionEntity descendant) {
        TransactionDescendant td = new TransactionDescendant();
        td.setDescendant(descendant);
        td.setParent(parent);
        td.setId(RandomUtils.nextLong(0, Long.MAX_VALUE));
        return td;
    }

    public static TransactionEntity entity(long id, double amount, String type, TransactionEntity parent) {
        TransactionEntity te = new TransactionEntity();
        te.setAmount(amount);
        te.setType(type);
        te.setId(id);
        te.setParent(parent);
        return te;
    }

    public static Transaction transaction(double amount, String type) {
        return transaction(amount, type, null);
    }

    public static Transaction transaction(double amount, String type, Long parentId) {
        Transaction t = new Transaction();
        t.setAmount(amount);
        t.setParentId(parentId);
        t.setType(type);
        return t;
    }

    public static TransactionEntity entity(long id, double amount, String type) {
        return entity(id, amount, type, null);
    }
}
