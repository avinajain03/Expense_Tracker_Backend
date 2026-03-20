package com.expensetracker.transaction.repository;

import com.expensetracker.transaction.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {

    Page<Transaction> findByUserIdOrderByDateDesc(String userId, Pageable pageable);

    List<Transaction> findByUserIdAndDateBetween(String userId, Instant from, Instant to);

    List<Transaction> findByUserIdAndAmountAndMerchantIgnoreCaseAndDateBetween(
            String userId, Double amount, String merchant, Instant from, Instant to);
}
