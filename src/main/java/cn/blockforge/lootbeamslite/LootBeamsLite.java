package cn.blockforge.lootbeamslite;

import cn.blockforge.lootbeamslite.client.ClientEvents;
import cn.blockforge.lootbeamslite.client.ConfigScreen;
import cn.blockforge.lootbeamslite.config.ClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(LootBeamsLite.MOD_ID)
public final class LootBeamsLite {
    public static final String MOD_ID = "z80z_loot_beam";

    public LootBeamsLite() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigScreen(parent)));
            MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        }
    }
}
