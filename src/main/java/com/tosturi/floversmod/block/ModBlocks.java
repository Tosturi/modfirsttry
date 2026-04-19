package com.tosturi.floversmod.block;

import com.tosturi.floversmod.FloVersMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.tosturi.floversmod.item.ModItems.ITEMS;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FloVersMod.MODID);

    // В NeoForge 2026-го года лучше использовать registerBlock
    // Он автоматически создаст и Block, и BlockItem, и свяжет их ID правильно.
    public static final DeferredBlock<Block> FLORICS_BOX = BLOCKS.register(
            "florics_box",
            registryName -> new Block(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, registryName))
                    .destroyTime(1.5f)
                    .explosionResistance(3.0f)
                    .sound(SoundType.WOOD)
            )
    );

    public static final DeferredItem<BlockItem> FLORICS_BOX_ITEM = ITEMS.registerSimpleBlockItem("florics_box", ModBlocks.FLORICS_BOX);
}