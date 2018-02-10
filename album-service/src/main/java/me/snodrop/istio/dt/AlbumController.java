package me.snodrop.istio.dt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@RestController
public class AlbumController {

    private static final List<Album> ALBUMS = Arrays.asList(
            new Album(1L, "Metallica", "...And Justice for All"),
            new Album(2L, "Iron Maiden", "Brave New World"),
            new Album(3L, "Disturbed", "Immortalized")
    );

    private final Random random = new Random();

    private final RestTemplate restTemplate;
    private final String albumDetailsServiceName;

    public AlbumController(RestTemplate restTemplate,
                           @Value("${service.album-details.name}") String albumDetailsServiceName) {
        this.restTemplate = restTemplate;
        this.albumDetailsServiceName = albumDetailsServiceName;
    }

    @RequestMapping("/random")
    public Album random(@RequestHeader HttpHeaders headers) {
        final HttpHeaders tracingHeaders = tracingHeaders(headers);

        final Album randomAlbum = getRandomAlbum();

        return randomAlbum.withDetails(
                restTemplate.exchange(
                        getURI(albumDetailsServiceName, randomAlbum.getId()),
                        HttpMethod.GET,
                        new HttpEntity<>(tracingHeaders),
                        Map.class
                ).getBody()
        );
    }

    private Album getRandomAlbum() {
        return ALBUMS.get(random.nextInt(ALBUMS.size()));
    }

    private URI getURI(String serviceName, Long albumId) {
        return URI.create(String.format("http://%s/%d", serviceName, albumId));
    }

    private static final List<String> HEADERS_THAT_NEED_TO_BE_PROPAGATES = Arrays.asList(
            "x-request-id",
            "x-b3-traceid",
            "x-b3-spanid",
            "x-b3-parentspanid",
            "x-b3-sampled",
            "x-b3-flags",
            "x-ot-span-context"
    );

    private HttpHeaders tracingHeaders(HttpHeaders allHeaders) {
        final List<Map.Entry<String, List<String>>> list =
                allHeaders
                        .entrySet()
                        .stream()
                        .filter(e -> HEADERS_THAT_NEED_TO_BE_PROPAGATES.contains(e.getKey()))
                        .collect(Collectors.toList());

        final HttpHeaders result = new HttpHeaders();
        list.forEach(e -> result.add(e.getKey(), e.getValue().get(0)));
        return result;
    }
}
