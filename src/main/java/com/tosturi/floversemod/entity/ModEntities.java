package com.tosturi.floversemod.entity;

import com.tosturi.floversemod.FloVerseMod;
import com.tosturi.floversemod.entity.custom.TigerGirlEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, FloVerseMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<TigerGirlEntity>> TIGER_GIRL =
            ENTITY_TYPES.register("tiger_girl",
                    registryName -> EntityType.Builder.of(TigerGirlEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.95F) // Размеры как у жителя
                            .build(ResourceKey.create(Registries.ENTITY_TYPE, registryName)));
}