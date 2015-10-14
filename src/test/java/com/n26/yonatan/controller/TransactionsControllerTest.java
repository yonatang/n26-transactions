package com.n26.yonatan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n26.yonatan.dto.Transaction;
import com.n26.yonatan.exception.NotFoundException;
import com.n26.yonatan.service.TransactionService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    public void saveTransaction_shouldReturnSavedTransaction() throws Exception {
    }

    @Test
    public void saveTransaction_shouldRejectFailedValidation_missingType() throws Exception {
    }

    @Test
    public void saveTransaction_shouldRejectFailedValidation_badTypeFormat() throws Exception {
    }

    @Test
    public void saveTransaction_shouldRejectFailedValidation_negativeAmount() throws Exception {
    }

    @Test
    public void sumTransactions_shouldReturnSum() throws Exception {
    }

    @Test
    public void sumTransactions_shouldHandleException() throws Exception {
    }

}