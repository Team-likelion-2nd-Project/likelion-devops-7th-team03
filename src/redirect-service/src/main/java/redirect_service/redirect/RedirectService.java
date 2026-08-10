package redirect_service.redirect;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RedirectService {

    private final LinkRepository linkRepository;

    /**
     * 외부 요청에는 링크의 존재 여부, 비공개 여부, 만료 여부를 구분해 노출하지 않는다.
     */
    public String findRedirectUrl(String slug) {
        Link link = linkRepository.findBySlug(slug)
                .orElseThrow(this::notFound);

        if (!link.isRedirectable(LocalDateTime.now())) {
            throw notFound();
        }

        return link.getOriginalUrl();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
