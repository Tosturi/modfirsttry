package com.tosturi.floversemod;

import com.tosturi.floversemod.entity.ModEntities;
import com.tosturi.floversemod.entity.client.TigerGirlModel;
import com.tosturi.floversemod.entity.client.TigerGirlRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;


@Mod(value = FloVerseMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = FloVerseMod.MODID, value = Dist.CLIENT)
public class FloVerseModClient {

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TigerGirlModel.LAYER_LOCATION, TigerGirlModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TIGER_GIRL.get(), TigerGirlRenderer::new);
    }
}
