package com.example.library.repository;

import com.example.library.domain.Book;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);

    List<Book> findByActiveTrueOrderByTitleAsc();

    @Query(value = """
            WITH query_param AS (
                SELECT\s
                    :query AS raw_query,
                    websearch_to_tsquery('simple', :query) AS ts_q
            ),
            search_results AS (
                SELECT\s
                    b.*,
                    ts_rank(b.search_vector, q.ts_q) AS rank_fts,
                    similarity(b.title, q.raw_query) AS sim_title,
                    similarity(b.authors, q.raw_query) AS sim_authors
                FROM book b
                CROSS JOIN query_param q
                WHERE b.active = true
                  AND (
                        b.search_vector @@ q.ts_q
                        OR b.title % q.raw_query
                        OR b.authors % q.raw_query
                        OR lower(b.category) LIKE lower(concat('%', q.raw_query, '%'))
                  )
            )
            SELECT *
            FROM search_results
            WHERE rank_fts > 0 OR sim_title > 0.20 OR sim_authors > 0.20 OR lower(category) LIKE lower(concat('%', :query, '%'))
            ORDER BY
                rank_fts DESC,
                GREATEST(sim_title, sim_authors) DESC,
                title ASC;
            """, nativeQuery = true)
    List<Book> search(@Param("query") String query);
}
