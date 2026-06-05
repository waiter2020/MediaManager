package com.mediamanager.search;

import com.mediamanager.search.service.FtsIndexService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FtsIndexServiceTest {

    @Test
    void toFtsQueryWrapsTokensWithPrefixMatch() {
        assertEquals("\"inception\"*", FtsIndexService.toFtsQuery("inception"));
        assertEquals("\"star\"* \"wars\"*", FtsIndexService.toFtsQuery("star wars"));
    }

    @Test
    void toFtsQueryEscapesQuotes() {
        String q = FtsIndexService.toFtsQuery("say \"hello\"");
        assertTrue(q.contains("\"\"hello\"\""));
    }

    @Test
    void toFtsQueryBlankReturnsEmpty() {
        assertEquals("", FtsIndexService.toFtsQuery("   "));
    }
}
