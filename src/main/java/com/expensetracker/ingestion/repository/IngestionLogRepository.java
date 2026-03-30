package com.expensetracker.ingestion.repository;

import com.expensetracker.ingestion.model.ParsingStatus;
import com.expensetracker.ingestion.model.RawIngestionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionLogRepository extends MongoRepository<RawIngestionLog, String> {

    Page<RawIngestionLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    boolean existsByUserIdAndRawContent(String userId, String rawContent);

    /** Only count non-FAILED prior entries as true duplicates — allows re-parsing after parser improvements. */
    boolean existsByUserIdAndRawContentAndParsingStatusNot(String userId, String rawContent, ParsingStatus status);
}
