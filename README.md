# Travis-CI Status: [![Build Status](https://travis-ci.org/yonatang/n26-transactions.svg?branch=master)](https://travis-ci.org/yonatang/n26-transactions)

# Prerequisites
* [Gradle](https://gradle.org/)
* [Lombok](https://projectlombok.org) IDE plugin, if you want your get the code compiled in your IDE. Eclipse and
  Intellij are supported. Not required if you just run it using gradle.

# Running it
* ```gradle bootRun``` 
    * It will setup a tomcat server on port 8080 and an h2 embedded server.
* ```gradle check``` to execute all tests

# Stack
* Java 8
    * Streams, Lambdas
    * CompletableFuture for testing
* Spring boot (web, data-jpa)
* Hibernate
* Hibernate Validators
* H2 as the in-memory db
* Logback for logging
* Guava for some java utils
* Testing:
    * Junit4 + Mockito + Hamcrest for unitests
    * Spring Test + RestAssured for integration tests
    * Using categories to separate slow tests and fast tests
 
# Assumptions
* ~~transaction.amount >= 0~~  After rethinking about it, transactions with negative amounts make a lot of sense.
* The transaction type must match ```[a-zA-Z0-9_]*```
    * Funny characters might break the REST api - in particular, type names such as "cars/bikes". I can overcome it, 
      but didn't want to go through the hassle.
* Type must be exists (not null or empty string)
* Type name length <= 45
* No security concerns
    * Spring security allow to easily defines roles, and define ACL policy both on the URLs and the service methods.
* Cannot override existing transaction id. Trying to PUT two transaction with same ID will cause one of them to fail.

# Discussion
## Performance and concurrency
* The [controller](src/main/java/com/n26/yonatan/controller/TransactionsController.java) and the 
  [service](src/main/java/com/n26/yonatan/service/TransactionService.java) are fully stateless. 
  That allows easy horizontal scaling (that is, in case of high load, add a new server and behind a load balancer). In 
  addition, it helps making sure the design is tolerant to concurrent usage.
* The state is kept in the DB. Two tables are used: ```transaction_entity``` and ```transaction_descendant```. The 
  first is used to store the data about the transactions (amount, type, parent id, category). The second is a helper 
  table that allows fast summing of a tree of transactions: the table maps each transaction to its the direct and 
  indirect descendant transactions. That way, summing the entire tree for a transaction require a single query on 
  that table.
    * Using a more naive approach - recursively travel on the graph (DFS) without a helper table - will require 
      plenty of different queries (order of number of transactions in the tree), which will cause unnecessary DB load 
      and higher latency.
    * Another approach is to keep in each of the transaction records the sum of all their descendants' amounts during 
      insertion of a new transaction. This allow to perform the ```/sum/``` request within O(1), but is prone to 
      concurrent record updates, which will slow down inserts of new transactions in high load environments. This can be
      solved using either optimistic locking or pessimistic locking. Both have draw-backs (complex implementation, 
      latency), so I preferred a lock-free implementation.
    * The ```type``` column is indexed as well, for better performances for the ```/types/``` requests.  
* In case the DB starts to get overloaded, a cluster is required - which requires a sharding strategy. 
    * The ```transaction_entity``` can be sharded by the transaction id (assuming it is uniformly distributed - 
      otherwise some kind of hashing is required).
    * The ```transaction_descendant``` should be sharded by the ```parent``` column, as every batch of operations 
      (inserts and selections) are related to single transaction id. Again, hashing might be required,
      depending on the id distribution.
      
## Hibernate
* Using ```@Version``` in the ```TransactionEntity``` forces hibernate to always INSERT those entities - allowing the DB
  to fail upon concurrent insertion two transactions with the same transaction id
* DTO Object were created to control and encapsulate the data exposed by the API.
* Since ```/types/``` queries require only list of IDs, a JPQL projection query was written, in order to reduce 
  the amount of traffic from the DB to the app.
* Same goes for the ```/sum/``` request - it query the db only for the amounts, and not the entire list of objects
    * Retrieving the list of objects will issue multiple SELECT commands to the DB, which is far less efficient. 
* A different hibernate entity was explicitly created for the ```TransactionDescendant``` object, instead of implicitly
  create it using a ```@ManyToMany``` and ```@JoinTable``` annotations, as the implicit table cannot be updated without
  updating the parent entity - which will cause locking issues.
* The actual SQL commands are logged during the integration tests - plenty of insights can be found by looking 
  at the actual way the app is communicating with the database
    
## Design
* A thin controller was created. It responsible to pass the relevant data to the service object, and to 
  gracefully return the errors and exceptions within a ```status``` object. It also validates the syntactical 
  correctness of the transactions, using Hibernate Validators.
* The service object handle the business logic. It interacts with the DB using Spring-generated repositories.
* Each method that updates the DB in the service marked as ```@Transactional``` to allow rollback upon failed insertion
* The repositories are spring-generated, and they are also validating the object prior persisting.
* Although a cyclic transaction cannot be created through the API, as the transactions are immutable, a defensive
  approach was taken when traveling through the graph to add descendants to the descendants table: in case during
  the travel a transaction was seen twice (that is, a loop was detected), the insert operation is rolled back and an 
  exception is thrown. This protects against a hanged thread due to DB error.
    * Throwing an exception is the far better option, as the first option will eventually shut the entire system down,
      while the latter will make sure the error affect only that specific user. It's easier to detect such issues using
      monitoring tools.
      

## Testing
* Unitests - Fast tests. Testing each class separately, while using Mockito to mock the rest. Not running within a 
  spring context.
    * The unitests covers 100% of the lines of interesting bits (the controller and the service) and 89% of the entire 
      code.
* Integration tests - Slow tests, but integrative. Running inside a real tomcat, using a real DB and a full spring context.
    * It contains tests and flows I would expect a QA person to think of:
        * Creating transactions
        * Query by types
        * Getting existing and non-existing transactions
        * Summing transactions with children, without children, and non existing transactions
        * Concurrent operations
        * Clean rollbacks verification
    * A single test method might test multiple requirements, in order to make it as fast as possible.
    * Emphasised concurrency testing - race conditions and rollbacks - which is hard to test with mocked environment.
    * Concurrency tests are repeated a few times, in order to increase the chances for race conditions to actually happen.
* All tests are continuously tests on a free Travis-CI server: https://travis-ci.org/yonatang/n26-transactions


## General
* Spring takes care to most of the non-functional requirements, such as JSON serialization and deserialization, 
  request mapping, transaction handling, jdbc connection, threading, etc.
* Using Spring Boot allow very portable and easy to execute artifact
    * Can be easily containerized in a docker container: https://spring.io/guides/gs/spring-boot-docker/
* Using gradle gives powerful dependency management without a verbose POM file
* Using lombok to reduce boilerplate getters and setters
* Using Github to store the codebase

