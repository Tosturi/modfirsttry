package com.tosturi.floversmod.item;

import com.tosturi.floversmod.FloVersMod;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FloVersMod.MODID);


    public static final DeferredItem<Item> FLORICS = ITEMS.registerItem(
            "florics",
            props -> new Item(props.stacksTo(64))
    );

}
