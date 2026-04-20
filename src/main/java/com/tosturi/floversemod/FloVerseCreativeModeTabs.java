package com.tosturi.floversemod;

import com.tosturi.floversemod.block.ModBlocks;
import com.tosturi.floversemod.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class FloVerseCreativeModeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FloVerseMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLOVERSE_TAB = CREATIVE_MODE_TABS.register(
            "floverse_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("floverse_tab.title"))
                    .icon(() -> ModItems.FLORICS.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                       output.accept(ModItems.FLORICS.get());
                       output.accept(ModBlocks.FLORICS_BOX_ITEM.get());
                    })
                    .build()
    );
}
