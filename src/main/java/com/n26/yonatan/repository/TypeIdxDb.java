package com.n26.yonatan.repository;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TypeIdxDb extends ConcurrentHashMap<String, Set<Long>> {
}
