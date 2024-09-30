package alt.mongodb.javadriver;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public class JavaDriverRepository {
    private static final String IMDB_VOTES = "imdb.votes";
    public static final String AWARDS_WINS = "awards.wins";

    private final MongoClient client;

    public JavaDriverRepository(MongoClient client) {
        this.client = client;
    }
    private List<Document> getGrade(String myColl, String[] lol) {
        return client.getDatabase("sample_mflix")
                .getCollection("movies")
                .find(
                        Filters.in("type", lol)
                )
                .into(new ArrayList<>());
    }

    private Document findBooksByGenre(String[] validGenres) {
        return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(in("genre", validGenres)).first();
    }
}
