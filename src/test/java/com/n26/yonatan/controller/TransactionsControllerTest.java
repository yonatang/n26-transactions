package com.n26.yonatan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n26.yonatan.dto.Sum;
import com.n26.yonatan.dto.Transaction;
import com.n26.yonatan.exception.NotFoundException;
import com.n26.yonatan.service.TransactionService;
import com.n26.yonatan.testutils.FastTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Category(FastTest.class)
@RunWith(MockitoJUnitRunner.class)
public class TransactionsControllerTest {

    @InjectMocks
    TransactionsController controller;

    MockMvc mockMvc;

    @Mock
    TransactionService transactionService;

    @Spy
    ObjectMapper objectMapper;

    @Before
    public void setupMock() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void getTransaction_shouldFindTransaction() throws Exception {
        Transaction t = new Transaction();
        t.setType("type");
        t.setAmount(1.1);
        t.setParentId(8L);
        when(transactionService.findTransaction(1)).thenReturn(t);
        mockMvc.perform(get("/transactionservice/transaction/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.amount", is(1.1)))
                .andExpect(jsonPath("$.type", is("type")))
                .andExpect(jsonPath("$.parent_id", is(8)));
    }

    @Test
    public void getTransaction_shouldHandleNotFoundException() throws Exception {
        when(transactionService.findTransaction(1)).thenThrow(new NotFoundException("missing transaction"));
        mockMvc.perform(get("/transactionservice/transaction/1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is("missing transaction")));
    }

    @Test
    public void createTransaction_shouldReturnSavedTransaction() throws Exception {
        Transaction t = new Transaction();
        t.setType("type");
        t.setAmount(1);
        mockMvc.perform(put("/transactionservice/transaction/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("status", is("ok")));
        verify(transactionService).createTransaction(1, t);
        verifyNoMoreInteractions(transactionService);
    }

    @Test
    public void createTransaction_shouldRejectFailedValidation_conflict() throws Exception {
        Transaction t = new Transaction();
        t.setType("type");
        t.setAmount(1);
        doThrow(DataIntegrityViolationException.class)
                .when(transactionService).createTransaction(1, t);

        mockMvc.perform(put("/transactionservice/transaction/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(t)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("status", is("conflict")));
    }

    @Test
    public void createTransaction_shouldRejectFailedValidation_missingType() throws Exception {
        Object[][] tests = new Object[][]{
                new Object[]{1.0, null}, //when type is null
                new Object[]{1.0, ""}, //when type is missing
                new Object[]{1.0, "bad format"}, //when type has bad format
                new Object[]{-1.0, "type"} //when amount is negative
        };
        for (Object[] test : tests) {
            Transaction t = new Transaction();
            t.setAmount((double) test[0]);
            t.setType((String) test[1]);
            mockMvc.perform(put("/transactionservice/transaction/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(t)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("status", not(is("ok"))));
            verifyZeroInteractions(transactionService);
        }
    }


    @Test
    public void sumTransactions_shouldReturnSum() throws Exception {
        when(transactionService.sumTransactions(1)).thenReturn(new Sum(5.5));
        mockMvc.perform(get("/transactionservice/sum/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("sum", is(5.5)));

    }

    @Test
    public void sumTransactions_shouldHandleException() throws Exception {
        when(transactionService.sumTransactions(1)).thenThrow(new NotFoundException("not found"));
        mockMvc.perform(get("/transactionservice/sum/1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("status", is("not found")));
    }

    @Test
    public void getTransactionsByType_shouldReturnTransactionIds() throws Exception {
        List<Long> ids = Arrays.asList(1L, 5L);
        when(transactionService.getTransactionIdsByType("cars")).thenReturn(ids);
        mockMvc.perform(get("/transactionservice/types/cars"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]", is(1)))
                .andExpect(jsonPath("$[1]", is(5)));
    }

}