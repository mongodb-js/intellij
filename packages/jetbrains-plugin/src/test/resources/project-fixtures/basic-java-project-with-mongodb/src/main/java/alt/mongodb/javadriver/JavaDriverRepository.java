package alt.mongodb.javadriver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class JavaDriverRepository {
    private static final String IMDB_VOTES = "imdb.votes";
    public static final String AWARDS_WINS = "awards.wins";

    private final MongoClient client;

    public JavaDriverRepository(MongoClient client) {
        this.client = client;
    }

    public Document findMovieById(String id) {
        return client
            .getDatabase("sample_mflix")
            .getCollection("movies")
            .find(Filters.eq(id))
            .first();
    }

    public List<Document> findMoviesByYear(String year) {
        return client
            .getDatabase("sample_mflix")
            .getCollection("movies")
            .find(Filters.eq("year", year))
            .into(new ArrayList<>());
    }

    public Document queryMovieById(String id) {
        return client
            .getDatabase("sample_mflix")
            .getCollection("movies")
            .aggregate(List.of(Aggregates.match(
                Filters.eq(id)
            )))
            .first();
    }

    public List<Document> queryMoviesByYear(String year) {
        return client
            .getDatabase("sample_mflix")
            .getCollection("movies")
            .aggregate(
                List.of(
                    Aggregates.match(
                        Filters.eq("_id", year)
                    ),
                    Aggregates.group(
                        "newField", Accumulators.avg("test", "$year")
                    ),
                    Aggregates.project(
                        Projections.fields(
                            Projections.include("year", "plot")
                        )
                    ),
                    Aggregates.sort(
                        Sorts.orderBy(
                            Sorts.ascending("asd", "qwe")
                        )
                    )
                )
            )
            .into(new ArrayList<>());
    }
}
