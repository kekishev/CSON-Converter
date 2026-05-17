package team.anonyms.converter.controllers.frontend.pagination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @param <T> a type of elements to paginate.
 */
@Component
public class PaginationHandler<T> {
    private static final Logger logger = LoggerFactory.getLogger(PaginationHandler.class);

    /**
     * @param objectsList {@link List} of any objects to make slice.
     * @param offset number of page.
     * @param limit number of elements per page.
     *
     * @return a slice of {@code objectsList}.
     * Note, that this method returns the empty slice if {@code offset} is negative or
     * is more than the offset the last non-empty slice of {@code objectsList}.
     */
    public List<T> makeSlice(List<T> objectsList, int offset, int limit) {
        List<T> slice = new ArrayList<>();

        if (objectsList == null || objectsList.isEmpty()) {
            return slice;
        }

        if (offset < 1) {
            logger.info("Offset must be positive; offset={}", offset);
            return slice;
        }

        if (limit * (offset - 1) > objectsList.size()) {
            logger.info("Number of page is out of range of provided elements; offset={}; objectsSize={}",
                    offset, objectsList.size());
        }

        if (limit * (offset) <= objectsList.size()) {
            slice = objectsList.subList(limit * (offset - 1), limit * offset);
        } else {
            if (limit * (offset - 1) < objectsList.size()) {
                slice = objectsList.subList(limit * (offset - 1), objectsList.size());
            }
        }

        return slice;
    }
}