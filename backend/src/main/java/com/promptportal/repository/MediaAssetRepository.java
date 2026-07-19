package com.promptportal.repository;

import com.promptportal.domain.MediaAsset;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends MongoRepository<MediaAsset, String> {

    List<MediaAsset> findByPromptIdOrderByCreatedAtDesc(String promptId);

    Optional<MediaAsset> findByIdAndPromptId(String id, String promptId);

    List<MediaAsset> findByOwnerId(String ownerId);

    void deleteByPromptId(String promptId);

    long countByPromptId(String promptId);

    @Aggregation(pipeline = {
            "{ $match: { ownerId: ?0 } }",
            "{ $group: { _id: null, total: { $sum: '$byteSize' } } }"
    })
    Long sumByteSizeByOwnerId(String ownerId);
}
