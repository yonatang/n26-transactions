package com.n26.yonatan;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.parsing.Parser;
import com.jayway.restassured.response.Response;
import com.n26.yonatan.dto.Status;
import com.n26.yonatan.dto.Transaction;
import com.n26.yonatan.model.TransactionEntity;
import com.n26.yonatan.repository.Db;
import com.n26.yonatan.repository.TypeIdxDb;
import com.n26.yonatan.service.TransactionService;
import com.n26.yonatan.testutils.SlowTest;
import com.n26.yonatan.testutils.Utils;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

/**
 * Integration tests for the app
 */
@Category(SlowTest.class)
@RunWith(SpringJUnit4ClassRunner.class)
@IntegrationTest("server.port:0")
@TestPropertySource("classpath:test-application.properties")
@SpringApplicationConfiguration(classes = Main.class)
@WebAppConfiguration
public class IT_ApplicationTests {

    @Autowired
    TransactionService transactionService;

    @Autowired
    Db db;

    @Autowired
    TypeIdxDb typeIdxDb;

    @Value("${local.server.port}")
    private int serverPort;

    @Before
    public void setup() {
        RestAssured.port = serverPort;
        RestAssured.defaultParser = Parser.JSON;
        RestAssured.requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).build();

        db.clear();
        typeIdxDb.clear();
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
        String longTypeName = StringUtils.repeat('a', 45);
        TransactionWrapper[] graph = new TransactionWrapper[]{
                transaction(1, 1.1, "type1"),
                transaction(2, 5.1, "type1", 1L),
                transaction(3, 7.1, "type2", 1L),
                transaction(4, 11.1, "type1", 2L),
                transaction(5, 13, "type2", 2L),
                transaction(6, 14, longTypeName), //test the maximum size of the type name
                transaction(7, 15, "abc_ABC_3"), //test the entire set of letters allowed
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
            putTransaction(t)
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
        putTransaction(graph[0])
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
                    new Object[]{longTypeName, newHashSet(6L)},
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
     * can be created when trying concurrently.<br>
     * In addition, it verify that a failure is rolled back properly.
     *
     * @throws Exception
     */
    @Test
    public void testConcurrentTransactionCreation() throws Exception {

        TransactionWrapper parent = transaction(9999, 5, "parent");
        putTransaction(parent)
                .then()
                .statusCode(OK.value());
        TransactionEntity parentEntity = db.get(parent.id);
        // Assert the parent saved to the DB
        assertThat(parentEntity, is(notNullValue()));

        double sum = 5;
        for (int i = 0; i < 10; i++) {
            TransactionWrapper tw = transaction(i, 1.1, "concurrent", parent.id);
            sum += 1.1;
            CompletableFuture<Status>[] statuses = new CompletableFuture[2];
            for (int j = 0; j < 2; j++) {
                statuses[j] = CompletableFuture.supplyAsync(() ->
                                putTransaction(tw)
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

            // Make sure the entity exists in the DB
            assertThat(db.get((long) i), is(notNullValue()));
            // Make sure no side effect when failing to insert
            assertThat(db.get(parent.id).getSum().get(), closeTo(sum, 0.001));


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
            putTransaction(tParent)
                    .then().statusCode(OK.value());

            CompletableFuture<Status>[] statuses = new CompletableFuture[2];
            for (int j = 0; j < 2; j++) {
                TransactionWrapper child = children[j];
                statuses[j] = CompletableFuture.supplyAsync(() ->
                                putTransaction(child)
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
     * This test validate the following save validations:<br>
     * 1. type not empty or null<br>
     * 2. type contains valid characters<br>
     * 3. parent must exists, if defined<br>
     */
    @Test
    public void validateTransactionSaveFlow() {
        Object[][] tests = new Object[][]{
                new Object[]{2.0, "", null}, //empty type is forbidden
                new Object[]{2.0, null, null}, //null type is forbidden
                new Object[]{2.0, "type.it", null}, //dot in the type is forbidden
                new Object[]{2.0, "type/it", null}, //slash in the type is forbidden
                new Object[]{2.0, "type", 123L}, //parent transaction must exists
                new Object[]{2.0 ,"type", 1L } // self pointing transaction are not allowed
        };
        for (Object[] test : tests) {
            TransactionWrapper tw = transaction(1, (double) test[0], (String) test[1], (Long) test[2]);
            putTransaction(tw).then()
                    .statusCode(BAD_REQUEST.value())
                    .and().body("status", not(is("ok")));
        }
    }


    private Response putTransaction(TransactionWrapper tw) {
        return given()
                .body(tw.transaction)
                .put("transactionservice/transaction/{id}", tw.id);
    }

    private TransactionWrapper transaction(long id, double amount, String type, Long parent) {
        Transaction transaction = Utils.transaction(amount, type, parent);
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
