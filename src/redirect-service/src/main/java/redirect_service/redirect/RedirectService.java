package redirect_service.redirect;

import lombok.extern.slf4j.Slf4j;
import redirect_service.exception.RedirectNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import redirect_service.clicklog.event.ClickRequestSnapshot;
import redirect_service.clicklog.event.RedirectSucceededEvent;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService {

    private final LinkRepository linkRepository;
    private final RedisRedirectCache redirectCache;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 리다이렉트가 가능한 경우에만 방문자 식별을 확정하고 원본 클릭 이벤트를 발행한다.
     * 이벤트 소비자의 포화·장애는 302 응답을 막지 않는다.
     */
    public RedirectTarget redirect(RedirectRequest request) {
        RedirectTarget target = findRedirectTarget(request.slug(), request.occurredAt());
        ClickRequestSnapshot snapshot = ClickRequestSnapshot.from(request);

        try {
            eventPublisher.publishEvent(new RedirectSucceededEvent(target.linkId(), snapshot));
        } catch (RuntimeException exception) {
            log.warn("Redirect event dispatch failed; redirect will continue. linkId={}", target.linkId(), exception);
        }
        return target;
    }

    public RedirectTarget findRedirectTarget(String slug) {
        return findRedirectTarget(slug, LocalDateTime.now(ZoneOffset.UTC));
    }

    public RedirectTarget findRedirectTarget(String slug, LocalDateTime utcNow) {
        // 캐시 우선 조회 (Cache Miss 시 DB 조회로 fallback)
        return findCachedRedirectTarget(slug, utcNow)
                .orElseGet(() -> findDatabaseRedirectTarget(slug, utcNow));
    }

    private Optional<RedirectTarget> findCachedRedirectTarget(String slug, LocalDateTime now) {
        Optional<RedirectCacheEntry> cachedEntry = redirectCache.get(slug);

        // 1. Cache Miss : 캐시에 데이터가 없는 경우
        if (cachedEntry.isEmpty()) {
            log.debug("Redirect cache miss. slug={}", slug);
            return Optional.empty();
        }
        
        // 2. Cache Hit : 캐시에 존재하지만, 리다이렉트 불가능한 상태인 경우
        log.debug("Redirect cache hit. slug={}", slug);
        RedirectCacheEntry entry = cachedEntry.get();
        if (!entry.isRedirectable(now)) {
            throw new RedirectNotFoundException();
        }

        // 3. Cache Hit & 정상 리다이렉트 가능
        return Optional.of(entry.toRedirectTarget());

    }

    private RedirectTarget findDatabaseRedirectTarget(String slug, LocalDateTime now) {
        // 1. DB에서 링크 조회 (아예 존재하지 않는 slug면 404 예외)
        Link link = linkRepository.findBySlug(slug)
                .orElseThrow(RedirectNotFoundException::new);

        // 2. 만료/비활성 링크도 캐시에 저장해 다음 요청을 DB 없이 404로 처리한다.
        RedirectCacheEntry cacheEntry = new RedirectCacheEntry(
                link.getId(),
                link.getOriginalUrl(),
                link.isEnabled(),
                link.getExpiresAt()
        );
        redirectCache.put(slug, cacheEntry);

        // 3. 리다이렉트 불가능한 상태면 예외 발생
        if (!link.isRedirectable(now)) {
            throw new RedirectNotFoundException();
        }

        return cacheEntry.toRedirectTarget();
    }
}
