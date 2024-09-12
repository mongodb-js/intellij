package alt.mongodb.springcriteria;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Document("movies")
record Movie() {

}

public class SpringCriteriaRepository {
    private final MongoTemplate template;

    public SpringCriteriaRepository(MongoTemplate template) {
        this.template = template;
    }

    private List<Movie> allMoviesWithRatingAtLeastReleasedAtLeastAt(int rating, Date releaseDate) {
        return template.find(
                query(where("awards.wins").gte(rating).and("released").gte(releaseDate)),
                Movie.class
        );
    }
}
