package de.janverhoeven.kafkastreamsbenchmarkapplication;

import de.janverhoeven.avro.Movie;
import de.janverhoeven.avro.Rating;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafkaStreams;

import javax.annotation.PostConstruct;

@EnableKafkaStreams
@Configuration
@Profile("!generator")
public class KafkaStreamsTopology {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaStreamsTopology.class);

    private final StreamsBuilder streamsBuilder;
    private final String topicMovies;
    private final String topicRatings;
    private final String topicMoviesRatingsAvg;
    private final String topicRatingsFiltered;
    private final String[] blacklistedWords = {"Lorem", "ipsum", "dolorem", "cumque"};

    public KafkaStreamsTopology(StreamsBuilder streamsBuilder,
                                @Value("${kafka.topics.movies}") String topicMovies,
                                @Value("${kafka.topics.ratings}") String topicRatings,
                                @Value("${kafka.topics.movies-ratings-avg}") String topicMoviesRatingsAvg,
                                @Value("${kafka.topics.ratings-filtered}") String topicRatingsFiltered) {
        this.streamsBuilder = streamsBuilder;
        this.topicMovies = topicMovies;
        this.topicRatings = topicRatings;
        this.topicMoviesRatingsAvg = topicMoviesRatingsAvg;
        this.topicRatingsFiltered = topicRatingsFiltered;
    }

    @PostConstruct
    public void createTopology() {
        this.createTopologyWithSpecificAvroRecods();
        // Print out the full topology
        LOGGER.info(this.streamsBuilder.build().describe().toString());
    }

    public void createTopologyWithSpecificAvroRecods() {
        // Input topics
        final KTable<String, Movie> movieKTable = streamsBuilder.table(topicMovies);
        final KStream<String, Rating> ratingsKStream = streamsBuilder.stream(topicRatings);

        // Filter ratings for blacklisted words
        KStream<String, Rating> ratingsFilteredKStream = ratingsKStream.filter((key, value) -> hasNoBlacklistedWords(value.getText().toString()));
        // Aggregate filtered ratings for each movie (for each message key) and join with Movie KTable
        KStream<String, Movie> avgMovieRatingKStream = ratingsFilteredKStream.groupByKey().reduce((rating1, rating2) -> {
                    double score1 = rating1.getScore() * rating1.getRatingCount();
                    double score2 = rating2.getScore() * rating2.getRatingCount();
                    int ratingCount = rating1.getRatingCount() + rating2.getRatingCount();
                    return new Rating("", (score1 + score2) / ratingCount, ratingCount);
                }).join(movieKTable, (averageRating, movie) -> {
                    movie.setAvgScore(averageRating.getScore());
                    return movie;
                })
                .toStream();

        // Write KStreams to output topics
        avgMovieRatingKStream.to(topicMoviesRatingsAvg);
        ratingsFilteredKStream.to(topicRatingsFiltered);
    }

    private boolean hasNoBlacklistedWords(String s) {
        for (String blacklistedWord : blacklistedWords) {
            if (s.contains(blacklistedWord)) {
                return false;
            }
        }
        return true;
    }
}
