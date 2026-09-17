package com.cappleapple.mastery.client.integration;

import com.cappleapple.mastery.client.ClientState;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.lang.reflect.Method;
import java.util.Map;

/** Optional Patchouli 1.21.1 integration. No Patchouli types enter Mastery's class signatures. */
@EventBusSubscriber(modid="mastery",value=Dist.CLIENT)
public final class PatchouliEditorGuide {
    public static final String FLAG="mastery_edit_mode";
    private static final ResourceLocation GUIDE=ResourceLocation.fromNamespaceAndPath("mastery","guide");
    private static Boolean previous;
    private static Level previousLevel;
    private static boolean unavailable;
    private static Object api;
    private static Method setFlag,openBook,getOpenBook;
    private PatchouliEditorGuide() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(unavailable||!ModList.get().isLoaded("patchouli"))return;
        Minecraft mc=Minecraft.getInstance();boolean enabled=mc.player!=null&&ClientState.editMode();
        if(previous!=null&&previous==enabled&&previousLevel==mc.level)return;
        try {
            if(api==null) {
                api=Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);
                Class<?> contract=Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI");
                setFlag=contract.getMethod("setConfigFlag",String.class,boolean.class);
                openBook=contract.getMethod("openBookGUI",ResourceLocation.class);
                getOpenBook=contract.getMethod("getOpenBookGui");
            }
            setFlag.invoke(api,FLAG,enabled);
            if(mc.level!=null) {
                Class<?> registry=Class.forName("vazkii.patchouli.common.book.BookRegistry");
                Object instance=registry.getField("INSTANCE").get(null);
                Object book=((Map<?,?>)registry.getField("books").get(instance)).get(GUIDE);
                if(book!=null) {
                    boolean wasOpen=GUIDE.equals(getOpenBook.invoke(api));
                    book.getClass().getMethod("reloadContents",Level.class,boolean.class).invoke(book,mc.level,false);
                    if(wasOpen)openBook.invoke(api,GUIDE);
                }
            }
            previous=enabled;previousLevel=mc.level;
        } catch(ReflectiveOperationException|LinkageError exception) {
            unavailable=true;
            LogUtils.getLogger().warn("Could not update optional Patchouli editor documentation",exception);
        }
    }
}
