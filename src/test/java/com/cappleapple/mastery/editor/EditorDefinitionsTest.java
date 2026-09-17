package com.cappleapple.mastery.editor;

import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.data.DefinitionLoader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EditorDefinitionsTest {
    private DefinitionSet fixture() {
        return DefinitionSet.fromJson(JsonParser.parseString("""
            {"trees":{"test:tree":{"name":"Tree"}},
             "nodes":{"test:parent":{"tree":"test:tree"},
                      "test:child":{"tree":"test:tree","dependencies":["test:parent"]}}}
            """).getAsJsonObject());
    }
    @Test void validEditCreatesSeparateSnapshot() {
        var original=fixture();
        var value=original.toJson().getAsJsonObject("nodes").getAsJsonObject("test:parent").deepCopy();
        value.addProperty("name","Changed");
        var changed=EditorDefinitions.change(original,"nodes","test:parent",value,false);
        assertEquals("Changed",changed.nodes().get("test:parent").name());
        assertNotEquals("Changed",original.nodes().get("test:parent").name());
    }
    @Test void referencedDeletionAndCycleAreRejected() {
        var original=fixture();
        assertThrows(RuntimeException.class,()->EditorDefinitions.change(original,"nodes","test:parent",new JsonObject(),true));
        var cyclic=JsonParser.parseString("{\"tree\":\"test:tree\",\"dependencies\":[\"test:child\"]}").getAsJsonObject();
        assertThrows(RuntimeException.class,()->EditorDefinitions.change(original,"nodes","test:parent",cyclic,false));
        assertEquals(2,original.nodes().size());
    }
    @Test void leafDeletionPreservesTreeAndParent() {
        var changed=EditorDefinitions.change(fixture(),"nodes","test:child",new JsonObject(),true);
        assertEquals(1,changed.nodes().size());
        assertTrue(changed.nodes().containsKey("test:parent"));
        assertTrue(changed.trees().containsKey("test:tree"));
    }
    @Test void pathsCannotLeaveWorldEditorPack() {
        Path root=Path.of("test-world","datapacks","mastery-editor");
        assertTrue(EditorDefinitions.checkedPath(root,"nodes","test:path/child").startsWith(root.toAbsolutePath().normalize()));
        for(String id:new String[]{"test:../pack","test:path/../../outside","..:node","test:/absolute","test:path//child"})
            assertThrows(IllegalArgumentException.class,()->EditorDefinitions.checkedPath(root,"nodes",id),id);
        assertThrows(IllegalArgumentException.class,()->EditorDefinitions.checkedPath(root,"../../bad","test:node"));
    }
}
