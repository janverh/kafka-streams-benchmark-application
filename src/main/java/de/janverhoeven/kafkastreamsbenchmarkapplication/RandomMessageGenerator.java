package de.janverhoeven.kafkastreamsbenchmarkapplication;

import com.github.javafaker.Faker;
import de.janverhoeven.avro.Movie;
import de.janverhoeven.avro.Rating;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;

@Component
@Profile("generator")
public class RandomMessageGenerator {

    private final KafkaTemplate<String, Movie> movieKafkaTemplate;
    private final KafkaTemplate<String, Rating> ratingKafkaTemplate;
    private final Integer limitRatings;
    private final String topicMovies;
    private final String topicRatings;

    //private final int words = 7; // ~200 Bytes In (each message)
    private final int ratingLength = 100;
    //private final int words = 113; // ~1KB In
    //private final int words = 641; // ~5KB In
    //private final int words = 3277; // ~25KB In
    //private final int words = 13166; // ~100KB In
    private final List<String> alreadySentMovieIds = new ArrayList<>();
    private final Random rand = new Random();

    public RandomMessageGenerator(KafkaTemplate<String, Movie> movieKafkaTemplate,
                                  KafkaTemplate<String, Rating> ratingKafkaTemplate,
                                  @Value("${kafka.generator.message-limit.ratings}") String limitRatings,
                                  @Value("${kafka.topics.movies}") String topicMovies,
                                  @Value("${kafka.topics.ratings}") String topicRatings) {
        this.movieKafkaTemplate = movieKafkaTemplate;
        this.ratingKafkaTemplate = ratingKafkaTemplate;
        this.limitRatings = Integer.parseInt(limitRatings);
        this.topicMovies = topicMovies;
        this.topicRatings = topicRatings;
    }

    @PostConstruct
    public void startGenerating() {
        for (int i = 0; i < this.limitRatings; i++) {
            // Add a new movie every few ratings
            if (i % 1000 == 0) {
                String key = UUID.randomUUID().toString();
                this.movieKafkaTemplate.send(topicMovies, key, newRandomMovie());
                alreadySentMovieIds.add(key);
            }
            // Send rating for one of the already produced movies
            this.ratingKafkaTemplate.send(topicRatings, oneOfAlreadySentMovieIds(), newRandomRating());
        }
    }

    private Movie newRandomMovie() {
        return Movie.newBuilder()
                .setTitle(Faker.instance().book().title())
                .setDirector(Faker.instance().funnyName().name())
                .setSummary(Faker.instance().lorem().paragraph().toString())
                .setAvgScore(null)
                .build();
    }

    private Rating newRandomRating() {
        return Rating.newBuilder()
                .setScore(Faker.instance().number().randomDouble(1, 1, 10))
                .setRatingCount(1)
		.setText(Faker.instance().lorem().characters(ratingLength).toString())
                .build();
    }

    private String oneOfAlreadySentMovieIds() {
        return alreadySentMovieIds.get(rand.nextInt(alreadySentMovieIds.size()));
    }

    @Deprecated
    // Just for measuring of message size
    private void messageSizeTest() throws InterruptedException {
        for (int i = 0; i < this.limitRatings; i++) {
            String key = UUID.randomUUID().toString();
            this.movieKafkaTemplate.send(topicMovies, key, newRandomMovie());
            alreadySentMovieIds.add(key);

            this.ratingKafkaTemplate.send(topicRatings, oneOfAlreadySentMovieIds(), newRandomRating());
            Thread.sleep(Duration.ofMillis(50).toMillis());
        }
    }

}
