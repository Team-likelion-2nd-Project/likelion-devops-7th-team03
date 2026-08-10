package redirect_service.redirect;

import redirect_service.common.exception.RedirectNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RedirectService {

    private final LinkRepository linkRepository;

    /**
     * 외부 요청에는 링크의 존재 여부, 비공개 여부, 만료 여부를 구분해 노출하지 않는다.
     */
    public RedirectTarget findRedirectTarget(String slug) {
        Link link = linkRepository.findBySlug(slug)
                .orElseThrow(this::notFound);

        if (!link.isRedirectable(LocalDateTime.now())) {
            throw notFound();
        }

        return new RedirectTarget(link.getId(), link.getOriginalUrl());
    }

    private RedirectNotFoundException notFound() {
        return new RedirectNotFoundException();
    }
}
