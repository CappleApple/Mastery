package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.editor.EditorService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class EditorGameTests {
    private EditorGameTests() {}
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void masteryReloadKeepsOtherDataManagers(GameTestHelper helper) {
        var server=helper.getLevel().getServer();
        var recipes=server.getRecipeManager();
        var resources=server.getResourceManager();
        var advancements=server.getAdvancements();
        var result=com.cappleapple.mastery.data.MasteryReloadListener.reloadOnly(server);
        helper.assertTrue(result.valid(),"Mastery-only reload rejected valid definitions");
        helper.assertTrue(recipes==server.getRecipeManager(),"Mastery reload replaced Minecraft's recipe manager");
        helper.assertTrue(resources==server.getResourceManager(),"Mastery reload replaced Minecraft's resource manager");
        helper.assertTrue(advancements==server.getAdvancements(),"Mastery reload replaced advancement data");
        helper.succeed();
    }
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void editorRequiresOperatorAndAffectsOnlyCaller(GameTestHelper helper) {
        var alice=MasteryTestPlayers.create(helper);
        var bob=MasteryTestPlayers.create(helper);
        var server=helper.getLevel().getServer();
        try {
            helper.assertTrue(!EditorService.handle(bob,"editor_get","mastery:fire","trees").isBlank(),"Player without edit mode could access editor");
            var dispatcher=server.getCommands().getDispatcher();
            boolean denied=false;
            try {dispatcher.execute("mastery edit_mode true",bob.createCommandSourceStack().withPermission(0));}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){denied=true;}
            helper.assertTrue(denied,"Non-operator command source enabled edit mode");
            server.getPlayerList().op(alice.getGameProfile());
            dispatcher.execute("mastery edit_mode true",alice.createCommandSourceStack().withPermission(4));
            helper.assertTrue(EditorService.enabled(alice),"Operator could not enable own editor");
            helper.assertTrue(!EditorService.enabled(bob),"Editor toggle affected another player");
            dispatcher.execute("mastery edit_mode false",alice.createCommandSourceStack().withPermission(4));
            helper.assertTrue(!EditorService.enabled(alice),"False did not disable editor");
            dispatcher.execute("mastery edit_mode true",alice.createCommandSourceStack().withPermission(4));
            server.getPlayerList().deop(alice.getGameProfile());
            helper.assertTrue(!EditorService.enabled(alice),"Revoked operator kept editor access");
            helper.succeed();
        }catch(Exception error){helper.fail("Editor command fixture failed: "+error.getMessage());}
        finally{EditorService.forget(alice);EditorService.forget(bob);server.getPlayerList().deop(alice.getGameProfile());alice.discard();bob.discard();}
    }
}
