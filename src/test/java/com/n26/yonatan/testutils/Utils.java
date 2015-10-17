package com.n26.yonatan.testutils;

import com.n26.yonatan.dto.Transaction;

/**
 * Created by yonatan on 15/10/2015.
 */
public class Utils {

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

}
