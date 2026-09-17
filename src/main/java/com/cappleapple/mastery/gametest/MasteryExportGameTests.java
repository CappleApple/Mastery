package com.cappleapple.mastery.gametest;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.MasteryReloadListener;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mastery")
@PrefixGameTestTemplate(false)
public final class MasteryExportGameTests {
    private MasteryExportGameTests() {}
    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty")
    public static void exportRequiresOperatorPlayerAndDoesNotReloadOrChangeProgress(GameTestHelper helper) {
        var alice=MasteryTestPlayers.create(helper); var bob=MasteryTestPlayers.create(helper);
        var server=helper.getLevel().getServer();
        var definitions=MasteryRuntime.definitions(); var resources=server.getResourceManager();
        var accepted=MasteryReloadListener.exportResources(); var bobBefore=MasteryRuntime.progress(bob).toJson();
        try {
            var dispatcher=server.getCommands().getDispatcher();
            boolean denied=false;
            try { dispatcher.execute("mastery export",alice.createCommandSourceStack().withPermission(0)); }
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied=true; }
            helper.assertTrue(denied,"Non-operator could export server definitions");
            denied=false;
            try { dispatcher.execute("mastery export",server.createCommandSourceStack().withPermission(4)); }
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied=true; }
            helper.assertTrue(denied,"Console exported without a receiving player");
            int result=dispatcher.execute("mastery export",alice.createCommandSourceStack().withPermission(2));
            helper.assertTrue(result==1,"Operator player could not prepare export transfer");
            helper.assertTrue(definitions==MasteryRuntime.definitions()&&resources==server.getResourceManager(),"Export reloaded server resources");
            helper.assertTrue(accepted.equals(MasteryReloadListener.exportResources()),"Export altered accepted raw resources");
            helper.assertTrue(bobBefore.equals(MasteryRuntime.progress(bob).toJson()),"Export affected another player's progression");
            helper.succeed();
        } catch(Exception error) { helper.fail("Export command fixture failed: "+error.getMessage()); }
        finally { MasteryRuntime.logout(alice);MasteryRuntime.logout(bob);alice.discard();bob.discard(); }
    }
}
