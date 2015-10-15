Travis-CI Status: [![Build Status](https://travis-ci.org/yonatang/n26-transactions.svg?branch=master)](https://travis-ci.org/yonatang/n26-transactions)

# Prerequisites
* [Gradle](https://gradle.org/)
* [Lombok](https://projectlombok.org) IDE plugin, if you want your get the code compiled in your IDE. Eclipse and
  Intellij are supported.

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
    * Using categories to separate slow tests and fast tests
 
# Assumptions
* ~~transaction.amount >= 0~~ After rethinking about it, transaction with negative amount make a lot of sense
* Transaction.type must match ```[a-zA-Z0-9_]*```
    * Funny values might break the REST api. In particular, types like "cars/bikes.json". I can overcome it, but didn't want
      to go through the hassle.
* Type must be exists
* No security concerns
* Cannot override existing transaction id. Trying to put existing transaction will fail 

# Discussion
## Performance and concurrency
* The controller and the service are fully stateless. That allows easy horizontal scaling (that is, in case of high load, 
  add a new server and register it with a load balancer). In addition, it helps making sure the design is tolerant
  to concurrent usage.
* The state is kept in the DB. Two tables are used: ```transaction_entity``` and ```transaction_descendant```. The 
  first is used to store the data about the transactions (amount, parent id, category). The second is a helper 
  table to allow fast summering of a tree of transactions: that table maps for each transaction the direct and indirect
  descendant transactions. That way, summering the entire tree for a single transaction require a single query on that 
  table.
    * Using a more naive approach - to recursively travel on the graph (DFS) without a helper table - will require 
      plenty of queries (if the tree is deep), which will cause unnecessary high DB load, and higher latency.
    * Another approach, which is to store in each transaction record the sum of all their descendants, and to update 
      upon creation of new descendant, is prone to concurrent record updates, which can be solved using either 
      optimistic locking or pessimistic locking. Both have draw-backs (complex implementation, latency), so a lock-free
      implementation is preferable, for better performance would achieved in high-load scenarios.
    * The ```type``` column is indexed as well, for better performances for the ```/types/``` requests.  
* In case the DB starts to get heated, a cluster is required - which require a sharding strategy. 
    * The ```transaction_entity``` can be sharded by the transaction id (assuming it is uniformely distributed - 
      otherwise somekind of hashing is required).
    * The ```transaction_descendant``` should be sharded by the ```parent``` column, as every batch of operations 
      (inserts and selections) are done using a single parent transaction id. Again, if the transaction id is not
      uniformly distributed, hashing is required
      
## Hibernate
* Using ```@Version``` in the ```TransactionEntity``` forces hibernate to always INSERT those entities - allowing the DB
  to fail upon concurrent insertion two transactions with the same transaction id
* DTO Object were created to control and encapsulate the data exposed by the AP
* Since ```/types/``` queries require only list of IDs, a projection JPQL query were written, in order to reduce 
  the amount of traffic from the DB to the app
* A different hibernate entity was explicitly created for the ```TransactionDescendant``` object, instead of implicitly
  create it using a ```@ManyToMany``` and ```@JoinTable``` annotations, as the implicit table cannot be updated without
  updating the parent entity - which will cause locking issues.
  
## Design
* A thin controller object was created. It responsible to pass the relevant data to the service object, and to 
  gracefully return the errors and exceptions within a ```status``` object. It also validates the syntactical correctness of the 
  transactions, using Hibernate Validators
* The service object handle the business logic. It interacts with the DB using Spring-generated repositories
* Each updating method in the service marked as ```@Transactional``` to allow rollback upon failed insertion
* The repositories are spring-generated, and also validating the object prior persisting
* Although a cyclic transaction cannot be created through the API, as the transactions are immutable, a defensive
  approach was taken when traveling through the graph to add descendants to the descendants table: in case during
  the travel a transaction was seen twice, the insert operation is rolled back and an exception is thrown. This protects 
  against a hanged thread due to DB error.
    * That is far better, as the first option will eventually shut the entire system down, while the latter will 
      make sure the error affect only that specific user. It's easier to detect such issues using monitoring tools.
      

## Testing
* Unitests - Fast tests. Testing each class separately, while using Mockito to mock the reast. Not running within a 
  spring context.
* Integration tests - Slow tests, but integrative. Running inside a real tomcat, using a real DB and 
  a full spring context.
    * Contains tests and flows I would expect a QA person to think of.
    * A single test method might test multiple requirements, in order to make it as fast as possible.
    * Emphasised concurrency testing - race conditions and rollbacks - which is hard to test with mocked environment
* All tests are continuously tests on a free Travis-CI server: https://travis-ci.org/yonatang/n26-transactions
* Execute ```gradle check``` to run all tests
* The actual SQL commands are logged during the integration tests - plenty of insights can be found by looking 
  at the actual way the app is communicating with the database

## General
* Spring takes care to most of the non-functional requirements, such as JSON serialization and deserialization, 
  request mapping, transaction handling, jdbc connection, threading, etc
* Using Spring Boot allow very portable and easy to execute artifact
    * Can be easily containerized in a docker container: https://spring.io/guides/gs/spring-boot-docker/
* Using gradle gives powerful dependency management without a verbose POM file
* Using lombok to reduce boilerplate getters and setters

