package com.cappleapple.mastery.client.gui.editor;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookGateEditorTest {
    @Test void enablingBlankTokenUsesNodeIdWithoutChangingOtherFields() {
        JsonObject source = EditorDrafts.node("test:tree", "test:parent");
        JsonObject changed = BookGateEditor.toggle(source, "test:child", true);
        assertEquals("test:child", BookGateEditor.token(changed));
        assertEquals("", BookGateEditor.token(source));
        assertEquals(source.get("dependencies"), changed.get("dependencies"));
    }
    @Test void enablingPreservesCustomTokenAndDisablingClearsIt() {
        JsonObject source = EditorDrafts.node("test:tree", ""); source.addProperty("book_token", "test:shared_book");
        assertEquals("test:shared_book", BookGateEditor.token(BookGateEditor.toggle(source, "test:node", true)));
        assertEquals("", BookGateEditor.token(BookGateEditor.toggle(source, "test:node", false)));
    }
    @Test void manualJsonEditsDriveTheControlAndSaveEnvelope() {
        JsonObject source = EditorDrafts.node("test:tree", ""); source.addProperty("book_token", "test:edited");
        assertEquals("test:edited", BookGateEditor.token(source));
        assertTrue(EditorDrafts.envelope("nodes", "test:node", source.toString(), 2).contains("test:edited"));
        source.addProperty("book_token", ""); assertTrue(BookGateEditor.token(source).isBlank());
    }
    @Test void copiedCommandUsesExistingGenericItemAndExactToken() {
        assertEquals("/give @s mastery:skill_book[minecraft:custom_data={mastery_unlock:\"test:branch\"}]", BookGateEditor.giveCommand("test:branch"));
        assertThrows(IllegalArgumentException.class, () -> BookGateEditor.giveCommand("bad token"));
    }
}
