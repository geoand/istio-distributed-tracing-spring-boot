package me.snodrop.istio.dt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class SuggestionController {

    private final RestTemplate restTemplate;
    private final AsyncRestTemplate asyncRestTemplate;

    private final String albumServiceName;
    private final String storeServiceName;

    public SuggestionController(RestTemplate restTemplate,
                                AsyncRestTemplate asyncRestTemplate,
                                @Value("${service.album.name}") String albumServiceName,
                                @Value("${service.store.name}") String storeServiceName) {
        this.restTemplate = restTemplate;
        this.asyncRestTemplate = asyncRestTemplate;
        this.albumServiceName = albumServiceName;
        this.storeServiceName = storeServiceName;
    }

    @RequestMapping("/serial")
    public Suggestion serial(@RequestHeader HttpHeaders headers) {
        final HttpHeaders tracingHeaders = tracingHeaders(headers);

        final ResponseEntity<Suggestion.Album> albumEntity =
                restTemplate.exchange(
                        getURI(albumServiceName, "random"),
                        HttpMethod.GET,
                        new HttpEntity<>(tracingHeaders),
                        Suggestion.Album.class
                        
                );

        final ResponseEntity<Suggestion.Store> storeEntity =
                restTemplate.exchange(
                        getURI(storeServiceName, "random"),
                        HttpMethod.GET,
                        new HttpEntity<>(tracingHeaders),
                        Suggestion.Store.class
                );

        return new Suggestion(albumEntity.getBody(), storeEntity.getBody());
    }

    @RequestMapping("/parallel")
    public Suggestion parallel(@RequestHeader HttpHeaders headers) throws Exception {
        final HttpHeaders tracingHeaders = tracingHeaders(headers);

        final ListenableFuture<ResponseEntity<Suggestion.Album>> albumFuture =
                asyncRestTemplate.exchange(
                        getURI(albumServiceName, "random"),
                        HttpMethod.GET,
                        new HttpEntity<>(tracingHeaders),
                        Suggestion.Album.class

                );

        final ListenableFuture<ResponseEntity<Suggestion.Store>> storeFuture =
                asyncRestTemplate.exchange(
                        getURI(storeServiceName, "random"),
                        HttpMethod.GET,
                        new HttpEntity<>(tracingHeaders),
                        Suggestion.Store.class
                );

        return new Suggestion(albumFuture.get().getBody(), storeFuture.get().getBody());
    }

    private URI getURI(String serviceName, String path) {
        return URI.create(String.format("http://%s/%s", serviceName, path));
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
