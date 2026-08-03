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
            SELECT b.*
            FROM book b
            WHERE b.active = true
              AND (
                    b.search_vector @@ websearch_to_tsquery('simple', :query)
                    OR similarity(b.title, :query) > 0.20
                    OR similarity(b.authors, :query) > 0.20
                    OR lower(coalesce(b.category, '')) LIKE lower(concat('%', :query, '%'))
              )
            ORDER BY
                ts_rank(b.search_vector, websearch_to_tsquery('simple', :query)) DESC,
                GREATEST(similarity(b.title, :query), similarity(b.authors, :query)) DESC,
                b.title ASC
            """, nativeQuery = true)
    List<Book> search(@Param("query") String query);
}
