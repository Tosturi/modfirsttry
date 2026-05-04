package com.tosturi.floversemod.entity.client;

import com.tosturi.floversemod.entity.custom.TigerGirlEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public class TigerGirlRenderer extends MobRenderer<TigerGirlEntity, TigerGirlRenderState, TigerGirlModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("floversemod", "textures/entity/tiger_girl.png");

    public TigerGirlRenderer(EntityRendererProvider.Context context) {
        super(context, new TigerGirlModel(context.bakeLayer(TigerGirlModel.LAYER_LOCATION)), 0.5f);
    }

    @Override
    public @NonNull TigerGirlRenderState createRenderState() {
        return new TigerGirlRenderState();
    }

    @Override
    public void extractRenderState(@NonNull TigerGirlEntity entity, @NonNull TigerGirlRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.idleAnimationState.copyFrom(entity.idleAnimationState);
        state.walkAnimationState.copyFrom(entity.walkAnimationState);
        state.runAnimationState.copyFrom(entity.runAnimationState);
        state.attackAnimationState.copyFrom(entity.attackAnimationState);
    }

    @Override
    public @NonNull Identifier getTextureLocation(@NonNull TigerGirlRenderState state) {
        return TEXTURE;
    }
}
