package net.shyshkin.war.sitecrawler.service;

import net.shyshkin.war.sitecrawler.dto.SearchRequest;
import net.shyshkin.war.sitecrawler.dto.VkCity;
import net.shyshkin.war.sitecrawler.dto.VkUser;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VkApiService {

    Mono<VkUser> getUser(Long userId);

    Mono<String> getUserJson(Long userId);

    Flux<VkUser> searchUsers(SearchRequest searchRequest);

    Mono<String> searchUsersJson(SearchRequest searchRequest);

    Flux<VkCity> getCities();

    Flux<VkCity> getCities(Pageable pageable);

    Mono<String> getCitiesJson(Pageable pageable);

    Mono<Integer> getCitiesCount();

}
