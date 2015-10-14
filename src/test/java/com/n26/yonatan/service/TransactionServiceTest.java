package com.n26.yonatan.service;

import com.n26.yonatan.dto.Transaction;
import com.n26.yonatan.exception.NotFoundException;
import com.n26.yonatan.model.TransactionEntity;
import com.n26.yonatan.repository.TransactionRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TransactionServiceTest {

    @InjectMocks
    TransactionService transactionService;

    @Mock
    TransactionRepository transactionRepository;

    private TransactionEntity entity(long id) {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(id);
        entity.setAmount(999.1);
        entity.setType("type" + id);
        return entity;
    }

    @Test
    public void findTransaction_shouldReturnParentlessTransaction() {
        TransactionEntity entity = entity(1);
        when(transactionRepository.findOne(1L)).thenReturn(entity);
        Transaction transaction = transactionService.findTransaction(1L);
        assertThat(transaction.getAmount(), is(999.1));
        assertThat(transaction.getType(), is("type1"));
        assertThat(transaction.getParentId(), is(nullValue()));

    }

    @Test
    public void findTransaction_shouldReturnTransactionWithParent() {
        TransactionEntity entity = entity(1);
        TransactionEntity parent = entity(2);
        entity.setParent(parent);
        when(transactionRepository.findOne(1L)).thenReturn(entity);

        Transaction transaction = transactionService.findTransaction(1L);
        assertThat(transaction.getAmount(), is(999.1));
        assertThat(transaction.getType(), is("type1"));
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
    public void saveTransaction_shouldSaveParentlessTransaction() {

    }

    @Test
    public void saveTransaction_shouldSaveTransactionWithParent() {

    }

    @Test
    public void saveTransaction_shouldFailSavingTransactionWithMissingParent() {

    }

    @Test
    public void sumTransactions_shouldSumChildlessTransaction() {

    }

    @Test
    public void sumTransactions_shouldSumTransactionWithChildren() {

    }

    @Test
    public void sumTransactions_shouldFailSummingMissingTransaction() {

    }

    @Test
    public void sumTransactions_shouldFailOnCircularTransaction() {

    }


}