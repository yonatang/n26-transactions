Travis-CI Status: [![Build Status](https://travis-ci.org/yonatang/n26-transactions.svg?branch=master)](https://travis-ci.org/yonatang/n26-transactions)

# Prerequisites
* [Gradle](https://gradle.org/)
* [Lombok](https://projectlombok.org) IDE plugin, if you want your get the code compiled in your IDE. Eclipse and
  Intellij supported.

# Running it
* ```gradle bootRun``` 
    * It will setup a tomcat server on port 8080 and h2 embedded server.

# Stack
* Java 8
    * Streams, Lambdas
    * CompletableFuture for testing
* Spring boot (web, data-jpa)
* H2 as the in-memory db
* Logback for logging
* Guava for some java utils
* Testing:
    * Junit4 + Mockito + Hamcrest for unitests
    * Spring Test + RestAssured for integration tests
    * Using categories to seperate slow tests and fast tests
 
# Assumptions
* ~~transaction.amount >= 0~~ After rethinking about it, transaction with negative amount make lots of sense
* transaction.type must match \[a-zA-Z0-9\]*
* type must be exists
* no security concerns
* cannot override existing transaction id

# Testing
* unitests (mockito) - fast. Mocks everything.
* Integration tests - slow, but integrative. Including real DB and full spring context testing
    * Contains tests and flows I would expect a QA person to think of

# Discussion
* Using @Version in the TransactionEntity forces hibernate to INSERT - which fails on concurrent creation
  of transaction with the same ID
* Summing can be done via DFS - but it would take lots of DB selections in case of a large 
  data structure.
    * Instead, generated a helper table that contains for each entity all their 
      descendants (direct and indirect) - which allows to calculate it in a single 
      DB selection
    * I'm using a different entity for that table in order to eliminate OptimisticLockException
      in cases two different descendants share the same ancestor while added concurrently
      (instead of using @ManyToMany and @JoinTable in the TransactionEntity object)
    * Pre-calculating children sums in the node might be even faster to fetch, but requires
      handling of Optimistic Locking exceptions - which I wanted to avoid. Those exception might
      happen quite frequently, if one ancestor have lots of children and descendants.
* Using DTO object to transfer data from in to the user - in order to encapsulate some of the internal db
  properties.
* the "Type" is indexed for querying it efficiently. 
* When quering by type, I'm using a hibernate projection
  to reduce amount of traffic from the DB (i.e. SELECT id FROM ...)
  
* Errors are handled gracefully. In particular, validation errors are resolved into "status" object.
  
* Although cycles are impossible via the API, implementation is defensive, and will throw exception
  when such transaction is detected. Can happen on corrupted database.
* Generated SQL can be shown if setting the relevant application.properties flags 

* Spring boot is easy to run and easy to containerized
* Added travis-ci CI
