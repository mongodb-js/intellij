package alt.mongodb.springcriteria;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;

@Document("comments")
record Comment(
    String name
) {}

public interface SpringQueryRepository extends Repository<Comment, ObjectId> {
    @Query("{ name: { $gt:  10, $lt:  200 } }")
    Optional<Comment> findBySomething(String something);
}

