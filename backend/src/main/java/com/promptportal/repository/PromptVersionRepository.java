package com.promptportal.repository;

import com.promptportal.domain.PromptVersion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PromptVersionRepository extends MongoRepository<PromptVersion, String> {

    List<PromptVersion> findByPromptIdOrderByVersionNumberDesc(String promptId);

    Optional<PromptVersion> findByPromptIdAndVersionNumber(String promptId, int versionNumber);

    Optional<PromptVersion> findFirstByPromptIdOrderByVersionNumberDesc(String promptId);

    void deleteByPromptId(String promptId);
}
