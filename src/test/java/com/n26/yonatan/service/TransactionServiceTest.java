package com.n26.yonatan.service;

import com.n26.yonatan.dto.Sum;
import com.n26.yonatan.dto.Transaction;
import com.n26.yonatan.exception.BadRequestException;
import com.n26.yonatan.exception.ConflictException;
import com.n26.yonatan.exception.NotFoundException;
import com.n26.yonatan.model.TransactionEntity;
import com.n26.yonatan.repository.Db;
import com.n26.yonatan.repository.TypeIdxDb;
import com.n26.yonatan.testutils.FastTest;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import static com.n26.yonatan.testutils.Utils.transaction;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@Category(FastTest.class)
@RunWith(MockitoJUnitRunner.class)
public class TransactionServiceTest {

    @InjectMocks
    TransactionService transactionService;

    @Spy
    Db db;

    @Spy
    TypeIdxDb typeIdxDb;


    @Test
    public void findTransaction_shouldReturnParentlessTransaction() {
        setNewEntity(1, 999.1, "type");

        Transaction transaction = transactionService.findTransaction(1L);

        assertThat(transaction.getAmount(), is(999.1));
        assertThat(transaction.getType(), is("type"));
        assertThat(transaction.getParentId(), is(nullValue()));
    }

    @Test
    public void findTransaction_shouldReturnTransactionWithParent() {
        setNewEntity(1, 999.1, "type", 2L);
        setNewEntity(2, 88, "type");

        Transaction transaction = transactionService.findTransaction(1L);

        assertThat(transaction.getAmount(), is(999.1));
        assertThat(transaction.getType(), is("type"));
        assertThat(transaction.getParentId(), is(2L));

    }

    @Test(expected = NotFoundException.class)
    public void findTransaction_shouldRejectMissingTransaction() {
        transactionService.findTransaction(1L);
    }

    @Test
    public void getTransactionIdsByType_shouldGetTransactionIds() {
        Set<Long> ids = new ConcurrentSkipListSet<>(Arrays.asList(1L, 5L));
        typeIdxDb.put("type", ids);

        Set<Long> result = transactionService.getTransactionIdsByType("type");

        assertThat(result, is(ids));
    }

    @Test
    public void getTransactionIdsByType_shouldGetTransactionIdsWhenNoTypeExists() {
        Set<Long> result = transactionService.getTransactionIdsByType("type");

        assertThat(result, notNullValue());
        assertThat(result, hasSize(0));
    }

    @Test
    public void createTransaction_shouldSaveParentlessTransaction() {
        Transaction t = transaction(1.1, "type");

        transactionService.createTransaction(2, t);

        assertEntity(2, 1.1, "type", null, 1.1);
    }

    @Test
    public void createTransaction_shouldAddToNewTypeIdx() {
        Transaction t = transaction(1.1, "type");

        transactionService.createTransaction(2, t);
        Set<Long> types = typeIdxDb.get("type");
        assertThat(types, notNullValue());
        assertThat(types, contains(2L));
    }

    @Test
    public void createTransaction_shouldAddToExistingTypeIdx() {
        Transaction t = transaction(1.1, "type");
        typeIdxDb.put("type", new ConcurrentSkipListSet<>(Arrays.asList(5L)));

        transactionService.createTransaction(2, t);
        Set<Long> types = typeIdxDb.get("type");
        assertThat(types, notNullValue());
        assertThat(types, contains(2L, 5L));
    }

    @Test
    public void createTransaction_shouldUpdateInHighConcurrent() {
        Transaction parent = transaction(1.1, "type");

        int N = 150;

        transactionService.createTransaction(1, parent);
        List<Pair<Long, Transaction>> ts = new ArrayList<>();
        double sum = 1.1;
        for (int i = 0; i < N; i++) {
            Transaction tChild = transaction(i + 1, "type", 1L);
            sum += i + 1;
            ts.add(Pair.of(Long.valueOf(i + 2), tChild));
        }

        ts.stream().parallel().forEach(pair -> {
            long idx = pair.getLeft();
            Transaction tx = pair.getRight();
            transactionService.createTransaction(idx, tx);
        });

        assertThat(db.size(), is(N + 1));
        assertThat(db.get(1L).getSum().get(), closeTo(sum, 0.001));
        assertThat(typeIdxDb.get("type"), hasSize(N + 1));
    }

    @Test
    public void createTransaction_shouldCreateTransactionWithParent() {
        Transaction tParent = transaction(1.1, "type", 2L);
        setNewEntity(2, 1.1, "type");

        transactionService.createTransaction(1, tParent);

        assertEntity(1, 1.1, "type", 2L, 1.1);
    }

    @Test
    public void createTransaction_shouldUpdateParentSum() {
        Transaction tParent = transaction(1.1, "type", 2L);
        TransactionEntity entity = setNewEntity(2, 1.1, "type");
        entity.getSum().set(1.1);

        transactionService.createTransaction(1, tParent);

        assertEntity(2, 1.1, "type", null, 2.2);
    }

    @Test(expected = BadRequestException.class)
    public void createTransaction_shouldFailSavingTransactionWithMissingParent() {
        Transaction t = transaction(1.1, "type", 2L);

        transactionService.createTransaction(1, t);
    }

    @Test(expected = BadRequestException.class)
    public void createTransaction_shouldFailSavingTransactionWithSelfParenting() {
        Transaction t = transaction(1.1, "type", 1L);

        transactionService.createTransaction(1, t);
    }

    @Test
    public void createTransaction_shouldFailToAddDuplicateTransactions() {
        TransactionEntity existingEntity = setNewEntity(1, 1.3, "type2");
        existingEntity.getSum().set(1.3);
        Transaction t = transaction(1.1, "type");

        try {
            transactionService.createTransaction(1, t);
            fail("ConflictException expected");
        } catch (ConflictException e) {
        }
        assertEntity(1, 1.3, "type2", null, 1.3);
    }

    @Test
    public void createTransaction_whenFailedToAddDupTransShouldNotChangeParentSums() {
        TransactionEntity parentEntity = setNewEntity(1, 1.3, "type2");
        TransactionEntity childEntity = setNewEntity(2, 1.5, "type2", 1L);
        childEntity.getSum().set(1.5);
        parentEntity.getSum().set(2.8);

        Transaction t = transaction(1.1, "type");

        try {
            transactionService.createTransaction(2, t);
            fail("ConflictException expected");
        } catch (ConflictException e) {
        }
        assertEntity(1, 1.3, "type2", null, 2.8);
    }

    @Test
    public void sumTransactions_shouldReturnSumValue() {

        TransactionEntity te = setNewEntity(1, 1.4, "type");
        te.getSum().set(1.4);
        Sum sum = transactionService.sumTransactions(1);
        assertThat(sum.getSum(), is(1.4));
    }

    @Test(expected = NotFoundException.class)
    public void sumTransactions_shouldFailSummingMissingTransaction() {
        transactionService.sumTransactions(1);
    }

    private void assertEntity(long id, double amount, String type, Long parentId, double sum) {
        TransactionEntity entity = db.get(id);
        assertThat(entity.getSum().get(), is(sum));
        assertThat(entity.getType(), is(type));
        assertThat(entity.getParentId(), parentId != null ? is(parentId) : nullValue());
        assertThat(entity.getAmount(), is(amount));
        assertThat(entity.getId(), is(id));
    }

    private TransactionEntity setNewEntity(long id, double amount, String type, Long parentId) {
        TransactionEntity entity = new TransactionEntity(id, parentId, type, amount);
        db.put(id, entity);
        return entity;
    }

    private TransactionEntity setNewEntity(long id, double amount, String type) {
        return setNewEntity(id, amount, type, null);
    }


}