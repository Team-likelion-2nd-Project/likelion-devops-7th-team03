package redirect_service.redirect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import redirect_service.clicklog.ClickLogService;
import redirect_service.clicklog.visitor.ResolvedVisitor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final RedirectService redirectService;
    private final ClickLogService clickLogService;

    @GetMapping("/{slug}")
    public ResponseEntity<Void> redirect(@PathVariable String slug, HttpServletRequest request) {
        RedirectTarget redirectTarget = redirectService.findRedirectTarget(slug);
        ResolvedVisitor visitor = clickLogService.capture(redirectTarget.linkId(), request);

        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectTarget.originalUrl()));
        if (visitor.setCookieHeader() != null) {
            response.header("Set-Cookie", visitor.setCookieHeader());
        }
        return response.build();
    }
}
