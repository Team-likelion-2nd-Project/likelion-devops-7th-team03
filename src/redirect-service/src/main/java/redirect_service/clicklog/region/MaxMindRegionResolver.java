package redirect_service.clicklog.region;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redirect_service.clicklog.ClickLogProperties;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

@Slf4j
@Component
@RequiredArgsConstructor
public class MaxMindRegionResolver implements RegionResolver {

    private final ClickLogProperties properties;
    private DatabaseReader databaseReader;

    @PostConstruct
    void initialize() {
        String databasePath = properties.getGeoIpDatabasePath();
        if (databasePath == null || databasePath.isBlank()) {
            log.warn("GeoIP database path is not configured; click event region will be null");
            return;
        }

        try {
            databaseReader = new DatabaseReader.Builder(new File(databasePath))
                    .withCache(new CHMCache())
                    .build();
        } catch (IOException exception) {
            log.warn("GeoIP database could not be loaded; click event region will be null", exception);
        }
    }

    @Override
    public String resolve(String clientIp) {
        if (databaseReader == null || clientIp == null || clientIp.isBlank()) {
            return null;
        }

        try {
            CityResponse response = databaseReader.city(InetAddress.getByName(clientIp));
            String country = response.getCountry().getIsoCode();
            String subdivision = response.getMostSpecificSubdivision().getIsoCode();

            if (country == null) {
                return null;
            }
            return subdivision == null ? country : country + "-" + subdivision;
        } catch (AddressNotFoundException ignored) {
            return null;
        } catch (IOException | GeoIp2Exception exception) {
            log.warn("GeoIP lookup failed", exception);
            return null;
        }
    }

    @PreDestroy
    void close() throws IOException {
        if (databaseReader != null) {
            databaseReader.close();
        }
    }
}
