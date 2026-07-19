package com.promptportal.repository;

import com.promptportal.domain.PromptDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PromptRepository extends MongoRepository<PromptDocument, String> {

    Optional<PromptDocument> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);

    Optional<PromptDocument> findByIdAndOwnerId(String id, String ownerId);

    @Query("{ 'ownerId': ?0, 'deletedAt': null, " +
            " $and: [ " +
            "   { $or: [ { $expr: { $eq: [?1, null] } }, { 'status': ?1 } ] }, " +
            "   { $or: [ { $expr: { $eq: [?2, null] } }, { 'mediaTypeFocus': ?2 } ] } " +
            " ] }")
    Page<PromptDocument> searchBasic(String ownerId, String status, String mediaTypeFocus, Pageable pageable);

    List<PromptDocument> findByOwnerIdAndDeletedAtIsNull(String ownerId);

    List<PromptDocument> findByLastModifiedAtBeforeAndDeletedAtIsNull(Instant cutoff);

    List<PromptDocument> findByLastModifiedAtBefore(Instant cutoff);
}
