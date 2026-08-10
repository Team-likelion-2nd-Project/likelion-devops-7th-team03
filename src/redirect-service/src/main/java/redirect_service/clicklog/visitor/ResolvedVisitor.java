package redirect_service.clicklog.visitor;

/** 새 방문자일 때만 setCookieHeader가 존재한다. */
public record ResolvedVisitor(String visitorId, String setCookieHeader) {
}
