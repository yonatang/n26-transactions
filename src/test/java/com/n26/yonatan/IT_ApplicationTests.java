package com.n26.yonatan;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.parsing.Parser;
import com.n26.yonatan.dto.Status;
import com.n26.yonatan.dto.Transaction;
import com.n26.yonatan.model.TransactionEntity;
import com.n26.yonatan.repository.TransactionDescendantRepository;
import com.n26.yonatan.repository.TransactionRepository;
import com.n26.yonatan.service.TransactionService;
import lombok.AllArgsConstructor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.concurrent.CompletableFuture;

import static com.google.common.collect.Sets.newHashSet;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static com.n26.yonatan.testutils.IsCloseTo.closeTo;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

/**
 * Integration tests for the app
 */
@RunWith(SpringJUnit4ClassRunner.class)
@IntegrationTest("server.port:0")
@TestPropertySource("classpath:test-application.properties")
@SpringApplicationConfiguration(classes = Main.class)
@WebAppConfiguration
public class IT_ApplicationTests {

    @Autowired
    TransactionService transactionService;

    @Autowired
    TransactionDescendantRepository transactionDescendantRepository;

    @Autowired
    TransactionRepository transactionRepository;

    @Value("${local.server.port}")
    private int serverPort;

    @Before
    public void setup() {
        RestAssured.port = serverPort;
        RestAssured.defaultParser = Parser.JSON;
        RestAssured.requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).build();

        transactionDescendantRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    /**
     * This test:<br>
     * 1. creates a forest of transactions<br>
     * 2. make sure the transactions does not exists before you create them<br>
     * 3. verify that they are exists after you save them<br>
     * <br>
     * After the forest was created, it verifies the all the subtrees in the
     * forest have the correct sums.
     * <br>
     * In addition, it verifies that querying by types works as expected.
     */
    @Test
    public void saveSumAndQueryFlow() {
        TransactionWrapper[] graph = new TransactionWrapper[]{
                transaction(1, 1.1, "type1"),
                transaction(2, 5.1, "type1", 1L),
                transaction(3, 7.1, "type2", 1L),
                transaction(4, 11.1, "type1", 2L),
                transaction(5, 13, "type2", 2L),
                transaction(99, 999.9, "type2")

        };

        //create transaction flow
        for (TransactionWrapper t : graph) {
            // check transaction doesn't exists
            when()
                    .get("transactionservice/transaction/{id}", t.id)
                    .then()
                    .statusCode(404);

            // create transaction
            given().body(t.transaction)
                    .put("transactionservice/transaction/{id}", t.id)
                    .then().statusCode(OK.value())
                    .body("status", is("ok"));

            // check transaction exists
            when()
                    .get("transactionservice/transaction/{id}", t.id)
                    .then()
                    .statusCode(OK.value())
                    .body("type", is(t.transaction.getType()))
                    .body("amount", equalTo((float) t.transaction.getAmount()));

        }
        // verify you cannot change transaction
        given().body(graph[0].transaction)
                .put("transactionservice/transaction/{id}", graph[0].id)
                .then().statusCode(CONFLICT.value())
                .body("status", is("conflict"));
        {
            // make sure the sums of every subtree in the forest are correct
            Object[][] tests = new Object[][]{
                    new Object[]{5, 13f},
                    new Object[]{4, 11.1f},
                    new Object[]{3, 7.1f},
                    new Object[]{2, 5.1f + (11.1f + 13f)},
                    new Object[]{1, (5.1f + (11.1f + 13f)) + (7.1f) + 1.1f},
                    new Object[]{99, 999.9f}
            };
            for (Object[] test : tests) {
                when()
                        .get("transactionservice/sum/{id}", test[0])
                        .then()
                        .statusCode(OK.value())
                        .body("sum", closeTo((float) test[1], 0.001f));
            }

            // verify proper error when summing non existing transaction
            when()
                    .get("transactionservice/sum/{id}", 1234)
                    .then()
                    .statusCode(NOT_FOUND.value())
                    .body("status", is("not found"));
        }

        {
            Object[][] tests = new Object[][]{
                    new Object[]{"type1", newHashSet(1L, 2L, 4L)},
                    new Object[]{"type2", newHashSet(3L, 5L, 99L)},
                    new Object[]{"type3", newHashSet()}
            };
            for (Object[] test : tests) {
                Long[] ids = when()
                        .get("transactionservice/types/{type}", test[0])
                        .then().statusCode(OK.value())
                        .extract().response().as(Long[].class);
                assertThat(newHashSet(ids), equalTo(test[1]));
            }

        }
    }

    /**
     * This test make sure one single transaction with a specific ID
     * can be created when trying concurrently
     *
     * @throws Exception
     */
    @Test
    public void testConcurrentTransactionCreation() throws Exception {
        for (int i = 0; i < 10; i++) {
            TransactionWrapper tw = transaction(i, 1.1, "concurrent");
            CompletableFuture<Status>[] statuses = new CompletableFuture[2];
            for (int j = 0; j < 2; j++) {
                statuses[j] = CompletableFuture.supplyAsync(() ->
                                given()
                                        .body(tw.transaction)
                                        .put("transactionservice/transaction/{id}", tw.id)
                                        .andReturn().as(Status.class)
                );
            }
            CompletableFuture.allOf(statuses).join();
            Status s1 = statuses[0].get();
            Status s2 = statuses[1].get();
            //make sure one of the status succeeded and one failed
            assertThat(s1.getStatus() + ":" + s2.getStatus(),
                    anyOf(
                            containsString("ok:conflict"),
                            containsString("conflict:ok")));
        }
    }

    /**
     * This test tries to concurrently add several children for the same parent
     *
     * @throws Exception
     */
    @Test
    public void testConcurrentDescendantAddition() throws Exception {
        for (int i = 0; i < 10 * 100; i += 100) {
            TransactionWrapper tParent = transaction(i, 1.1, "concurrent");
            TransactionWrapper[] children = new TransactionWrapper[]{
                    transaction(i + 1, 1.1, "concurrent", (long) i),
                    transaction(i + 2, 1.1, "concurrent", (long) i)
            };
            given()
                    .body(tParent.transaction)
                    .put("transactionservice/transaction/{id}", tParent.id)
                    .then().statusCode(OK.value());

            CompletableFuture<Status>[] statuses = new CompletableFuture[2];
            for (int j = 0; j < 2; j++) {
                TransactionWrapper child = children[j];
                statuses[j] = CompletableFuture.supplyAsync(() ->
                                given()
                                        .body(child.transaction)
                                        .put("transactionservice/transaction/{id}", child.id)
                                        .andReturn().as(Status.class)
                );
            }
            CompletableFuture.allOf(statuses).join();
            Status s1 = statuses[0].get();
            Status s2 = statuses[1].get();
            assertThat(s1.getStatus(), is("ok"));
            assertThat(s2.getStatus(), is("ok"));
        }
    }

    /**
     * Corrupt the underlying data structure with cyclic transaction
     * and make sure the server doesn't hang when trying to use it
     */
    @Test
    public void shouldStopCyclicTransaction() {
        //create a cycle
        TransactionEntity t1 = new TransactionEntity();
        t1.setType("type");
        t1.setId(1L);
        t1.setAmount(1);
        transactionRepository.save(t1);

        TransactionEntity t2 = new TransactionEntity();
        t2.setType("type");
        t2.setId(2L);
        t2.setAmount(1);
        t2.setParent(t1);
        transactionRepository.save(t2);
        t1.setParent(t2);
        transactionRepository.save(t1);

        TransactionWrapper tw = transaction(3, 1, "type", 1L);
        given().body(tw.transaction)
                .put("transactionservice/transaction/{id}", tw.id)
                .then()
                .statusCode(INTERNAL_SERVER_ERROR.value())
                .and().body("status", is("cyclic transaction"));


    }

    /**
     * This test validate the following save validations:<br>
     * 1. amount >= 0<br>
     * 2. type not empty or null<br>
     * 3. type contains valid characters<br>
     * 4. parent must exists, if defined<br>
     */
    @Test
    public void validateTransactionSaveFlow() {
        Object[][] tests = new Object[][]{
                new Object[]{-1.0, "type", null},
                new Object[]{2.0, "", null},
                new Object[]{2.0, null, null},
                new Object[]{2.0, "type.it", null},
                new Object[]{2.0, "type/it", null},
                new Object[]{2.0, "type", 123L}
        };
        for (Object[] test : tests) {
            TransactionWrapper tw = transaction(1, (double) test[0], (String) test[1], (Long) test[2]);
            given().body(tw.transaction)
                    .put("transactionservice/transaction/{id}", tw.id)
                    .then()
                    .statusCode(BAD_REQUEST.value())
                    .and().body("status", not(is("ok")));
        }
    }


    private TransactionWrapper transaction(long id, double amount, String type, Long parent) {
        Transaction transaction = new Transaction();
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setParentId(parent);
        return new TransactionWrapper(transaction, id);
    }

    @AllArgsConstructor
    class TransactionWrapper {
        Transaction transaction;
        long id;
    }

    private TransactionWrapper transaction(long id, double amount, String type) {
        return transaction(id, amount, type, null);
    }


}
