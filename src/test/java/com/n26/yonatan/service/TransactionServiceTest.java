package com.n26.yonatan.service;

import com.n26.yonatan.dto.Sum;
import com.n26.yonatan.dto.Transaction;
import com.n26.yonatan.exception.BadRequestException;
import com.n26.yonatan.exception.NotFoundException;
import com.n26.yonatan.exception.ServerErrorException;
import com.n26.yonatan.model.TransactionEntity;
import com.n26.yonatan.repository.TransactionDescendantRepository;
import com.n26.yonatan.repository.TransactionRepository;
import com.n26.yonatan.testutils.FastTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.n26.yonatan.testutils.Utils.descendant;
import static com.n26.yonatan.testutils.Utils.entity;
import static com.n26.yonatan.testutils.Utils.transaction;
import static java.util.Collections.emptyList;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Category(FastTest.class)
@RunWith(MockitoJUnitRunner.class)
public class TransactionServiceTest {

    @InjectMocks
    TransactionService transactionService;

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    TransactionDescendantRepository transactionDescendantRepository;

    @Test
    public void findTransaction_shouldReturnParentlessTransaction() {
        TransactionEntity entity = entity(1, 999.1, "type");
        setupFindTransaction(entity);

        Transaction transaction = transactionService.findTransaction(1L);

        assertThat(transaction.getAmount(), is(999.1));
        assertThat(transaction.getType(), is("type"));
        assertThat(transaction.getParentId(), is(nullValue()));
    }

    @Test
    public void findTransaction_shouldReturnTransactionWithParent() {
        TransactionEntity entity = entity(1, 999.1, "type");
        TransactionEntity parent = entity(2, 88, "type");
        entity.setParent(parent);
        setupFindTransaction(entity);

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
        List<Long> list = new ArrayList<>();
        when(transactionRepository.getTransactionIdsByType("type")).thenReturn(list);

        List<Long> result = transactionService.getTransactionIdsByType("type");

        assertThat(result, is(list));
    }

    @Test
    public void createTransaction_shouldSaveParentlessTransaction() {
        Transaction t = transaction(1.1, "type");
        TransactionEntity te = entity(2, 1.1, "type");

        transactionService.createTransaction(2, t);

        verify(transactionRepository).save(te);
    }

    @Test
    public void createTransaction_shouldCreateTransactionWithParent() {
        Transaction tParent = transaction(1.1, "type", 2L);
        TransactionEntity te2 = entity(2, 1.1, "type");
        TransactionEntity te1 = entity(1, 1.1, "type", te2);
        setupFindTransaction(te2);

        transactionService.createTransaction(1, tParent);

        verify(transactionRepository).findOne(2L);
        verify(transactionRepository).save(te1);
        verify(transactionDescendantRepository).save(descendant(0, te2, te1));

    }

    @Test(expected = BadRequestException.class)
    public void createTransaction_shouldFailSavingTransactionWithMissingParent() {
        Transaction t = transaction(1.1, "type", 2L);

        transactionService.createTransaction(1, t);
    }

    private void setupFindTransaction(TransactionEntity te) {
        when(transactionRepository.findOne(te.getId())).thenReturn(te);
    }

    @Test(expected = ServerErrorException.class, timeout = 500)
    public void createTransaction_shouldFailOnCircularTransaction() {
        TransactionEntity te2 = entity(2, 1.1, "type");
        TransactionEntity te1 = entity(1, 1.1, "type", te2);
        te2.setParent(te1);

        setupFindTransaction(te1);
        setupFindTransaction(te2);
        Transaction t = transaction(1.1, "type", te1.getId());

        transactionService.createTransaction(3, t);
    }

    @Test
    public void sumTransactions_shouldSumChildlessTransaction() {
        TransactionEntity te = entity(1, 1.3, "type");
        setupFindTransaction(te);
        when(transactionDescendantRepository.findByParent(te)).thenReturn(emptyList());

        Sum sum = transactionService.sumTransactions(1);
        assertThat(sum.getSum(), is(1.3));
    }

    @Test
    public void sumTransactions_shouldSumTransactionWithChildren() {
        TransactionEntity tParent = entity(1, 1.3, "type");
        TransactionEntity te1 = entity(2, 1.1, "type");
        TransactionEntity te2 = entity(3, 1.5, "type");

        setupFindTransaction(tParent);
        when(transactionDescendantRepository.findByParent(tParent))
                .thenReturn(Arrays.asList(descendant(tParent, te1), descendant(tParent, te2)));

        Sum sum = transactionService.sumTransactions(1);
        assertThat(sum.getSum(), closeTo(1.3 + 1.1 + 1.5, 0.001));
    }

    @Test(expected = NotFoundException.class)
    public void sumTransactions_shouldFailSummingMissingTransaction() {
        transactionService.sumTransactions(1);
    }


}