package com.n26.yonatan.repository;

import com.n26.yonatan.model.TransactionEntity;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class Db extends ConcurrentHashMap<Long, TransactionEntity> {
}
