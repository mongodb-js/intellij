package alt.mongodb.javadriver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class JavaDriverRepository {
    private static final String IMDB_VOTES = "imdb.votes";
    public static final String AWARDS_WINS = "awards.wins";

    private final MongoCollection<Document> movies;

    public JavaDriverRepository(MongoClient client) {
        this.movies = client.getDatabase("sample_mflix")
                .getCollection("movies");
    }

    private List<Document> getAllReleasedMoviesAfterDateWithAwards(Date releaseDate) {
        return movies
                .find(Filters.and(
                        Filters.gte("released", releaseDate),
                        Filters.gt("awards.wins", 0)
                ))
                .into(new ArrayList<>());
    }


    private Document getMovieById(ObjectId id) {
        return movies
                .find(Filters.eq("_id", id))
                .first();
    }
}
