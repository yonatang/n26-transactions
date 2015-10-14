package com.n26.yonatan.controller;

import com.n26.yonatan.dto.Status;
import com.n26.yonatan.dto.Sum;
import com.n26.yonatan.dto.Transaction;
import com.n26.yonatan.exception.HttpException;
import com.n26.yonatan.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

/**
 * The Transaction Service controller
 */
@RestController
@RequestMapping("transactionservice")
@Slf4j
public class TransactionsController {

    @Autowired
    private TransactionService transactionService;

    @RequestMapping(value = "transaction/{transactionId}", method = RequestMethod.GET)
    public Transaction getTransaction(@PathVariable long transactionId) {
        log.trace("getTransaction {}", transactionId);
        return transactionService.findTransaction(transactionId);
    }

    @RequestMapping(value = "transaction/{transactionId}", method = RequestMethod.PUT)
    public Status saveTransaction(@PathVariable long transactionId, @Valid @RequestBody Transaction transaction) {
        log.trace("createTransaction {} {}", transactionId, transaction);
        transactionService.createTransaction(transactionId, transaction);
        return new Status("ok");
    }

    @RequestMapping(value = "types/{type}", method = RequestMethod.GET)
    public List<Long> getTransactionsByType(@PathVariable(value = "type") String type) {
        log.trace("getTransactionsByType {}", type);
        return transactionService.getTransactionIdsByType(type);
    }

    @RequestMapping(value = "sum/{transactionId}", method = RequestMethod.GET)
    public Sum sumTransactions(@PathVariable long transactionId) {
        log.trace("sumTransactions {}", transactionId);
        return transactionService.sumTransactions(transactionId);
    }

    @ExceptionHandler(HttpException.class)
    public ResponseEntity<Status> handleException(HttpException e, HttpServletRequest req) {
        log.debug("Exception {} thrown when {} {}", e.getMessage(), req.getMethod(), req.getServletPath());
        Status status = new Status(e.getMessage());
        status.setPath(req.getServletPath());
        ResponseStatus responseStatus = AnnotationUtils.findAnnotation(e.getClass(), ResponseStatus.class);
        return new ResponseEntity<>(status, responseStatus.value());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Status> handleException(MethodArgumentNotValidException e, HttpServletRequest req) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> "[" + fieldError.getField() + "] " + fieldError.getDefaultMessage())
                .reduce((s1, s2) -> s1 + ", " + s2)
                .get();
        log.debug("Invalid request for {}: {} when {} {}", e.getBindingResult().getTarget(), errors,
                req.getMethod(), req.getServletPath());
        Status status = new Status("Invalid request: " + errors);
        status.setPath(req.getServletPath());
        return new ResponseEntity<>(status, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Status> handleConflicts(DataIntegrityViolationException e, HttpServletRequest req) {
        log.debug("Conflic when inserting entity when {} {}", req.getMethod(), req.getServletPath());
        Status status = new Status("conflict");
        status.setPath(req.getServletPath());
        return new ResponseEntity<>(status, HttpStatus.CONFLICT);
    }

}
