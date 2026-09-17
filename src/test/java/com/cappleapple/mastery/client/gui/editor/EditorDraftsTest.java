package com.cappleapple.mastery.client.gui.editor;

import com.cappleapple.mastery.data.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorDraftsTest {
    @Test void treeTemplateUsesOnePointPerLevelAndOutwardSection() {
        TreeDefinition tree = (TreeDefinition) DefinitionLoader.parse("trees", "test:tree", EditorDrafts.tree());
        assertEquals(1, tree.pointEvery());
        assertEquals("south", tree.section());
    }
    @Test void childTemplatePreservesExactParentAndTree() {
        NodeDefinition node = (NodeDefinition) DefinitionLoader.parse("nodes", "test:child", EditorDrafts.node("test:tree", "test:parent"));
        assertEquals("test:tree", node.tree());
        assertEquals(java.util.List.of(new Dependency("test:parent", 1)), node.dependencies());
        assertEquals("", node.bookToken());
        assertEquals("available", node.visibility());
        assertTrue(node.spell().isBlank());
    }
    @Test void rootChildHasNoFakeRootPrerequisite() {
        NodeDefinition node = (NodeDefinition) DefinitionLoader.parse("nodes", "test:child", EditorDrafts.node("test:tree", ""));
        assertTrue(node.dependencies().isEmpty());
    }
    @Test void saveEnvelopeKeepsRevisionAndExactDefinitionFields() {
        JsonObject definition = EditorDrafts.node("test:tree", "test:parent");
        definition.addProperty("book_token", "test:secret");
        JsonObject payload = JsonParser.parseString(EditorDrafts.envelope("nodes", "test:child", definition.toString(), 47)).getAsJsonObject();
        assertEquals(47, payload.get("revision").getAsLong());
        assertEquals("nodes", payload.get("kind").getAsString());
        assertEquals(definition, payload.getAsJsonObject("definition"));
    }
    @Test void invalidJsonAndIdentifiersCannotSend() {
        assertThrows(RuntimeException.class, () -> EditorDrafts.envelope("nodes", "test:node", "[]", 0));
        assertThrows(RuntimeException.class, () -> EditorDrafts.envelope("nodes", "not a valid id", EditorDrafts.node("test:tree", "").toString(), 0));
        assertThrows(RuntimeException.class, () -> EditorDrafts.envelope("trees", "test:tree", "{", 0));
    }
    @Test void limitIncludesProtocolEnvelopeNotJustDefinitionText() {
        JsonObject definition = EditorDrafts.tree();
        definition.addProperty("description", "x".repeat(EditorDrafts.MAX_PAYLOAD));
        assertThrows(IllegalArgumentException.class, () -> EditorDrafts.envelope("trees", "test:tree", definition.toString(), 0));
        definition.addProperty("description", "x".repeat(7000));
        assertTrue(EditorDrafts.envelope("trees", "test:tree", definition.toString(), 0).length() <= EditorDrafts.MAX_PAYLOAD);
    }
}
