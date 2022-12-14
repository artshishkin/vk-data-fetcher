package net.shyshkin.war.sitecrawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shyshkin.war.sitecrawler.config.VkApiConfigData;
import net.shyshkin.war.sitecrawler.dto.*;
import net.shyshkin.war.sitecrawler.exception.VkApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class VkApiServiceImpl implements VkApiService {

    private static final int REQUEST_CITIES_MAX_SIZE = 1000;
    private final WebClient vkApiClient;
    private final VkApiConfigData configData;

    @Override
    public Mono<VkUser> getUser(Long userId) {
        return getUser(userId, VkUserResponse.class)
                .doOnNext(response -> {
                    if (response.getError() != null)
                        throw new VkApiException(response.getError().toString());
                })
                .flatMapIterable(VkUserResponse::getResponse)
                .next()
                .doOnNext(vkUser -> log.debug("User: {}", vkUser));
    }

    @Override
    public Mono<String> getUserJson(Long userId) {
        return getUser(userId, String.class)
                .doOnNext(jsonResponse -> log.debug("User Response: {}", jsonResponse));
    }

    @Override
    public Flux<VkUser> searchUsers(SearchRequest searchRequest) {
        return searchUsers(searchRequest, VkSearchUserResponse.class)
                .doOnNext(response -> {
                    if (response.getError() != null)
                        throw new VkApiException(response.getError().toString());
                })
                .map(VkSearchUserResponse::getResponse)
                .flatMapIterable(VkSearchUserResponse.SearchUserResponse::getItems)
                .doOnNext(jsonResponse -> log.debug("Search User Response: {}", jsonResponse));
    }

    @Override
    public Mono<String> searchUsersJson(SearchRequest searchRequest) {
        return searchUsers(searchRequest, String.class)
                .doOnNext(jsonResponse -> log.debug("Search User Response: {}", jsonResponse));
    }

    @Override
    public Flux<VkCity> getCities() {
        return getCitiesCount()
                .flatMapMany(count -> Flux.range(0, count / REQUEST_CITIES_MAX_SIZE + 1))
                .map(pageIndex -> PageRequest.of(pageIndex, REQUEST_CITIES_MAX_SIZE))
                .delayElements(Duration.ofMillis(350L)) //max 3 req/sec
                .flatMap(this::getCities);
    }

    @Override
    public Flux<VkCity> getCities(Pageable pageable) {
        log.debug("Getting cities...");
        return getCities(pageable, VkCitiesResponse.class)
                .doOnNext(response -> {
                    if (response.getError() != null)
                        throw new VkApiException(response.getError().toString());
                })
                .map(VkCitiesResponse::getResponse)
                .flatMapIterable(VkCitiesResponse.CitiesResponse::getItems);
    }

    @Override
    public Mono<String> getCitiesJson(Pageable pageable) {
        log.debug("Getting cities full JSON...");
        return getCities(pageable, String.class);
    }

    @Override
    public Mono<Integer> getCitiesCount() {
        log.debug("Getting cities count...");
        return getCities(Pageable.ofSize(1), VkCitiesResponse.class)
                .map(VkCitiesResponse::getResponse)
                .map(VkCitiesResponse.CitiesResponse::getCount);
    }

    private <T> Mono<T> getUser(Long userId, Class<T> T) {
        return vkApiClient.get()
                .uri(builder -> builder
                        .path(configData.getUserEndpoint())
                        .queryParam("user_id", userId)
                        .queryParam("fields", configData.getFields())
                        .build())

                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(VkApiException::new))
                .bodyToMono(T);
    }

    private <T> Mono<T> searchUsers(SearchRequest searchRequest, Class<T> T) {
        log.debug("Searching for: {}", searchRequest);
        return vkApiClient.get()
                .uri(builder -> builder
                        .path(configData.getSearchEndpoint())
                        .queryParam("q", searchRequest.getName())
                        .queryParam("city", searchRequest.getCity())
                        .queryParam("country", 1)
                        .queryParam("birth_day", searchRequest.getBday())
                        .queryParam("birth_month", searchRequest.getBmonth())
                        .queryParam("birth_year", searchRequest.getByear())
                        .queryParam("fields", configData.getFields())
                        .build())
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(VkApiException::new))
                .bodyToMono(T);
    }

    private <T> Mono<T> getCities(Pageable pageable, Class<T> T) {
        return Mono.just(pageable)
                .doOnNext(p -> {
                    if (pageable.getPageSize() <= 0 || pageable.getPageSize() > REQUEST_CITIES_MAX_SIZE)
                        throw new IllegalArgumentException("Page size must be positive and less then 1000");
                })
                .then(vkApiClient.get()
                        .uri(builder -> builder
                                .path(configData.getCitiesEndpoint())
                                .queryParam("country_id", 1)
                                .queryParam("need_all", 1)
                                .queryParam("count", pageable.getPageSize())
                                .queryParam("offset", pageable.getOffset())
                                .build())
                        .exchangeToMono(response -> {
                            log.debug("Status code: {}", response.statusCode());
                            log.debug("Headers: {}", response.headers().asHttpHeaders());
                            return response.bodyToMono(T);
                        })
                );
    }

}
