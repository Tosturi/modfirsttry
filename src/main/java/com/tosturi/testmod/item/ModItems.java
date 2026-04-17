package com.tosturi.testmod.item;

import com.tosturi.testmod.TestMod;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TestMod.MODID);


    public static final DeferredItem<Item> FLORICS = ITEMS.registerItem(
            "florics",
            (Item.Properties props) -> new Item(props.stacksTo(64))
    );

//    public static void register(IEventBus eventBus) {
//        ITEMS.register(eventBus);
//    }
}
