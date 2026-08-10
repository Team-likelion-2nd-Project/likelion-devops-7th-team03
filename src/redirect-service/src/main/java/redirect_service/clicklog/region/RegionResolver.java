package redirect_service.clicklog.region;

public interface RegionResolver {

    /** 국가/행정구역 ISO 코드. GeoIP DB가 없거나 찾지 못하면 null을 반환한다. */
    String resolve(String clientIp);
}
