package com.example.management.auth.repository;

import com.example.management.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(Long kakaoId);

    /** JWT subject(User.userId, 외부 노출용 UUID)로 내부 id를 조회할 때 쓴다 */
    Optional<User> findByUserId(String userId);
}
