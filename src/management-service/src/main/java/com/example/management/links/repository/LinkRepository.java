package com.example.management.links.repository;

import com.example.management.links.domain.Link;
import org.springframework.data.jpa.repository.JpaRepository;

/** 링크 생성 시 slug 중복을 피하기 위한 최소 DB 접근. */
public interface LinkRepository extends JpaRepository<Link, Long> {

    boolean existsBySlug(String slug);

    long countByUserIdAndIsVisibleTrue(Long userId);
}
