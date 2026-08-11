package com.example.management.url_link.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByLinkIdAndUserIdAndIsVisibleTrue(String linkId, Long userId);

    List<Link> findByLinkIdInAndUserIdAndIsVisibleTrue(List<String> linkIds, Long userId);
}
