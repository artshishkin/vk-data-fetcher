package net.shyshkin.war.vkstreamingapi.service;

import lombok.RequiredArgsConstructor;
import net.shyshkin.war.vkstreamingapi.model.StreamingEventIndex;
import net.shyshkin.war.vkstreamingapi.repository.StreamingEventRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class EventsService {

    private final StreamingEventRepository repository;
    private final ContentService contentService;

    public Mono<StreamingEventIndex> updateEvent(StreamingEventIndex eventIndex) {
        return Mono.just(eventIndex)
                .flatMap(streamingEventIndex -> contentService.readContent(streamingEventIndex.getEvent().getEventUrl())
                        .doOnNext(streamingEventIndex::setContent)
                        .thenReturn(streamingEventIndex)
                )
                .flatMap(repository::save);
    }

    public Mono<StreamingEventIndex> updateEvent(String eventId) {
        return repository.findById(eventId)
                .flatMap(streamingEventIndex -> contentService.readContent(streamingEventIndex.getEvent().getEventUrl())
                        .doOnNext(streamingEventIndex::setContent)
                        .thenReturn(streamingEventIndex)
                )
                .flatMap(repository::save);
    }

    public Flux<StreamingEventIndex> updateAllEvents() {
        return repository.findAll()
                .limitRate(3)
                .delayElements(Duration.ofMillis(500))
                .flatMap(streamingEventIndex -> contentService.readContent(streamingEventIndex.getEvent().getEventUrl())
                        .doOnNext(streamingEventIndex::setContent)
                        .thenReturn(streamingEventIndex)
                )
                .flatMap(repository::save);
    }

    public Mono<Void> deleteEvent(String eventId) {
        return repository.deleteById(eventId);
    }

    public Flux<String> getEventIdsWithText(String searchText) {
        return repository.findEventsWithText(searchText)
                .map(StreamingEventIndex::getId);
    }

    public Flux<String> deleteEventsWithText(String searchText) {
        return repository.findEventsWithText(searchText)
                .map(StreamingEventIndex::getId)
                .flatMap(id -> repository.deleteById(id).thenReturn(id));
    }

}
