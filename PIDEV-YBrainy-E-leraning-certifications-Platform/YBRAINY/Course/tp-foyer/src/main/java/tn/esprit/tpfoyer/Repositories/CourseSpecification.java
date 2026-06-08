package tn.esprit.tpfoyer.Repositories;

import jakarta.persistence.criteria.Predicate;
import tn.esprit.tpfoyer.Entities.Course;
import tn.esprit.tpfoyer.Entities.enums.CourseCategory;
import tn.esprit.tpfoyer.Entities.enums.CourseLevel;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CourseSpecification {

    /**
     * Splits keyword by comma, ORs each trimmed term across title and description.
     * e.g. "python, programming, basics" → title/desc LIKE '%python%'
     *   OR title/desc LIKE '%programming%'
     *   OR title/desc LIKE '%basics%'
     */
    public static Specification<Course> searchByKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) return null;
            String[] terms = keyword.split("[,]+");
            List<Predicate> termPredicates = new ArrayList<>();
            for (String term : terms) {
                String t = term.trim();
                if (t.isBlank()) continue;
                String pattern = "%" + t.toLowerCase() + "%";
                termPredicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                ));
            }
            if (termPredicates.isEmpty()) return null;
            return cb.or(termPredicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Course> hasCategory(CourseCategory category) {
        return (root, query, cb) ->
                category == null ? null : cb.equal(root.get("category"), category);
    }

    public static Specification<Course> hasLevel(CourseLevel level) {
        return (root, query, cb) ->
                level == null ? null : cb.equal(root.get("level"), level);
    }

    public static Specification<Course> hasPriceGreaterThanOrEqual(BigDecimal minPrice) {
        return (root, query, cb) ->
                minPrice == null ? null : cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Course> hasPriceLessThanOrEqual(BigDecimal maxPrice) {
        return (root, query, cb) ->
                maxPrice == null ? null : cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Course> isPublished(Boolean isPublished) {
        return (root, query, cb) ->
                isPublished == null ? null : cb.equal(root.get("isPublished"), isPublished);
    }

    public static Specification<Course> hasInstructorId(Long instructorId) {
        return (root, query, cb) ->
                instructorId == null ? null : cb.equal(root.get("instructorId"), instructorId);
    }
}
