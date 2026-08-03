CREATE UNIQUE INDEX uk_book_isbn
    ON book(isbn)
    WHERE isbn IS NOT NULL;