package team.anonyms.converter.controllers.frontend.pagination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaginationHandlerTest {

    private PaginationHandler<String> paginationHandler;
    private List<String> testData;

    @BeforeEach
    void setUp() {
        paginationHandler = new PaginationHandler<>();
        testData = List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");
    }

    @Test
    void testMakeSlice_FirstPage_Success() {
        List<String> result = paginationHandler.makeSlice(testData, 1, 3);

        assertEquals(3, result.size());
        assertEquals(List.of("A", "B", "C"), result);
    }

    @Test
    void testMakeSlice_MiddlePage_Success() {
        List<String> result = paginationHandler.makeSlice(testData, 2, 3);

        assertEquals(3, result.size());
        assertEquals(List.of("D", "E", "F"), result);
    }

    @Test
    void testMakeSlice_LastPagePartial_Success() {
        List<String> result = paginationHandler.makeSlice(testData, 4, 3);

        assertEquals(1, result.size());
        assertEquals(List.of("J"), result);
    }

    @Test
    void testMakeSlice_ExactLastPage_Success() {
        List<String> result = paginationHandler.makeSlice(testData, 2, 5);

        assertEquals(5, result.size());
        assertEquals(List.of("F", "G", "H", "I", "J"), result);
    }

    @Test
    void testMakeSlice_OutOfRange_ReturnsEmptyList() {
        List<String> result = paginationHandler.makeSlice(testData, 5, 3);

        assertTrue(result.isEmpty());
    }

    @Test
    void testMakeSlice_EmptyInputList_ReturnsEmptyList() {
        List<String> result = paginationHandler.makeSlice(List.of(), 1, 3);

        assertTrue(result.isEmpty());
    }

    @Test
    void testMakeSlice_OffsetLessThanOne_ThrowsException() {
        List<String> result = paginationHandler.makeSlice(List.of(), 0, 3);

        assertTrue(result.isEmpty());
    }
}