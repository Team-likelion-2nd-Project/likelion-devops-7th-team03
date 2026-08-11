package redirect_service.redirect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import redirect_service.config.RedirectCacheProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRedirectCache {

    private static final String KEY_PREFIX = "link:";

    private static final String LINK_ID_FIELD = "linkId";
    private static final String ORIGINAL_URL_FIELD = "originalUrl";
    private static final String ENABLED_FIELD = "isEnabled";
    private static final String EXPIRES_AT_FIELD = "expiresAt";

    /**
     * - KEYS[1]: Redis Key (예: "link:abc")
     * - ARGV[1~4]: Hash 필드 값들 (linkId, originalUrl, isEnabled, expiresAt)
     * - ARGV[5]: TTL (초 단위)
     */
    private static final RedisScript<String> PUT_CACHE_SCRIPT = RedisScript.of(
            "redis.call('HSET', KEYS[1], '" + LINK_ID_FIELD + "', ARGV[1], '"
                    + ORIGINAL_URL_FIELD + "', ARGV[2], '"
                    + ENABLED_FIELD + "', ARGV[3], '"
                    + EXPIRES_AT_FIELD + "', ARGV[4]); " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[5]); " +
                    "return 'OK';",
            String.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final RedirectCacheProperties properties;

    public Optional<RedirectCacheEntry> get(String slug) {
        try {
            Map<Object, Object> fields = stringRedisTemplate.opsForHash().entries(key(slug));

            if (fields.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(toEntry(fields));
        } catch (Exception exception) {
            log.warn("캐시 조회 중 예외가 발생하여 DB 조회를 수행합니다. slug={}", slug, exception);
            return Optional.empty();
        }
    }

    public void put(String slug, RedirectCacheEntry entry) {
        try {
            // Lua Script를 사용하여 1 RT 및 원자적 실행 처리
            stringRedisTemplate.execute(
                    PUT_CACHE_SCRIPT,
                    List.of(key(slug)),                                            // KEYS[1]
                    entry.linkId().toString(),                                      // ARGV[1]
                    entry.originalUrl(),                                            // ARGV[2]
                    Boolean.toString(entry.isEnabled()),                            // ARGV[3]
                    entry.expiresAt() == null ? "" : entry.expiresAt().toString(),  // ARGV[4]
                    String.valueOf(properties.getTtl().getSeconds())               // ARGV[5]
            );
        } catch (Exception exception) {
            log.warn("캐시 저장(put) 중 예외가 발생했으나 캐시 없이 처리를 진행합니다. slug={}", slug, exception);
        }
    }

    private String key(String slug) {
        return KEY_PREFIX + slug;
    }

    private RedirectCacheEntry toEntry(Map<Object, Object> fields) {
        String expiresAt = valueOf(fields, EXPIRES_AT_FIELD);
        return new RedirectCacheEntry(
                Long.parseLong(Objects.requireNonNull(valueOf(fields, LINK_ID_FIELD))),
                valueOf(fields, ORIGINAL_URL_FIELD),
                Boolean.parseBoolean(valueOf(fields, ENABLED_FIELD)),
                expiresAt == null || expiresAt.isBlank() ? null : LocalDateTime.parse(expiresAt)
        );
    }

    private String valueOf(Map<Object, Object> fields, String fieldName) {
        Object value = fields.get(fieldName);
        return value == null ? null : value.toString();
    }
}
