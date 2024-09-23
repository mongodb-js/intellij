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
    private List<Document> getGrade(String myColl) {
        return client.getDatabase("sample_mflix")
                .getCollection("")
                .find(
                        Filters.and(
                                Filters.eq(IMDB_VOTES, 1),
                                Filters.eq(AWARDS_WINS, 1)
                        )
                )
                .into(new ArrayList<>());
    }
}
