package com.tosturi.floversemod.entity.client;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

public class TigerGirlModel extends EntityModel<TigerGirlRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath("floversemod", "tiger_girl"), "main");

    private final ModelPart head;
    private final KeyframeAnimation idleAnimation;
    private final KeyframeAnimation walkAnimation;
    private final KeyframeAnimation runAnimation;
    private final KeyframeAnimation attackAnimation;

    public TigerGirlModel(ModelPart root) {
        super(root);
        this.head = root.getChild("Head");
        this.idleAnimation   = TigerGirlModelAnimations.IDLE.bake(root);
        this.walkAnimation   = TigerGirlModelAnimations.WALK.bake(root);
        this.runAnimation    = TigerGirlModelAnimations.RUN.bake(root);
        this.attackAnimation = TigerGirlModelAnimations.ATTACK.bake(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Head = partdefinition.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(25, 1).addBox(3.0F, -9.25F, -1.0F, 2.0F, 2.0F, 1.25F, new CubeDeformation(0.0F))
                .texOffs(1, 2).addBox(-5.0F, -9.25F, -1.0F, 2.0F, 2.0F, 1.24F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        Head.addOrReplaceChild("Hair", CubeListBuilder.create().texOffs(32, 48).addBox(-4.0F, -7.0F, -4.0F, 8.0F, 7.0F, 8.0F, new CubeDeformation(0.4F))
                .texOffs(34, 34).addBox(-5.0F, -7.0F, -3.0F, 1.0F, 5.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(42, 17).addBox(4.0F, -7.0F, -3.0F, 1.0F, 5.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 56).addBox(-3.75F, -8.5F, -3.5F, 7.5F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition whiskers = Head.addOrReplaceChild("whiskers", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        whiskers.addOrReplaceChild("whiskers_6_r1", CubeListBuilder.create().texOffs(0, 7).addBox(-1.0F, -0.05F, -1.0F, 2.0F, 0.05F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.25F, -2.25F, -3.25F, 0.0F, -0.2909F, 0.0F));
        whiskers.addOrReplaceChild("whiskers_5_r1", CubeListBuilder.create().texOffs(0, 7).addBox(-1.0F, -0.05F, -1.0F, 2.0F, 0.05F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.25F, -1.75F, -3.25F, 0.0945F, -0.2909F, -0.3193F));
        whiskers.addOrReplaceChild("whiskers_4_r1", CubeListBuilder.create().texOffs(0, 7).addBox(-1.0F, -0.05F, -1.0F, 2.0F, 0.05F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.25F, -2.75F, -3.25F, -0.0945F, -0.2909F, 0.3193F));
        whiskers.addOrReplaceChild("whiskers_3_r1", CubeListBuilder.create().texOffs(0, 7).mirror().addBox(-1.0F, -0.05F, -1.0F, 2.0F, 0.05F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(2.25F, -2.75F, -3.25F, -0.0945F, 0.2909F, -0.3193F));
        whiskers.addOrReplaceChild("whiskers_2_r1", CubeListBuilder.create().texOffs(0, 7).mirror().addBox(-1.0F, -0.05F, -1.0F, 2.0F, 0.05F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(2.25F, -1.75F, -3.25F, 0.0945F, 0.2909F, 0.3193F));
        whiskers.addOrReplaceChild("whiskers_1_r1", CubeListBuilder.create().texOffs(0, 7).mirror().addBox(-1.0F, -0.05F, -1.0F, 2.0F, 0.05F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(2.25F, -2.25F, -3.25F, 0.0F, 0.2909F, 0.0F));

        partdefinition.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(0, 17).addBox(-4.0F, -24.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(1, 69).addBox(-4.25F, -12.1F, -2.25F, 8.5F, 3.0F, 4.5F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        partdefinition.addOrReplaceChild("Right Hand", CubeListBuilder.create().texOffs(17, 34).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, 2.0F, 0.0F));

        partdefinition.addOrReplaceChild("Left Hand", CubeListBuilder.create().texOffs(0, 34).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, 2.0F, 0.0F));

        partdefinition.addOrReplaceChild("Right Leg", CubeListBuilder.create().texOffs(25, 17).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(49, 41).addBox(-2.0F, 11.0F, -3.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, 12.0F, 0.0F));

        partdefinition.addOrReplaceChild("Left Leg", CubeListBuilder.create().texOffs(33, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(50, 6).addBox(-2.0F, 11.0F, -3.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, 12.0F, 0.0F));

        PartDefinition Tail_Root = partdefinition.addOrReplaceChild("Tail_Root", CubeListBuilder.create(), PartPose.offset(0.0F, 10.5F, 2.0F));
        PartDefinition Tail_1 = Tail_Root.addOrReplaceChild("Tail_1", CubeListBuilder.create().texOffs(54, 26).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition Tail_2 = Tail_1.addOrReplaceChild("Tail_2", CubeListBuilder.create().texOffs(44, 29).addBox(-0.99F, -0.97F, -0.2F, 1.98F, 1.98F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 3.0F));
        PartDefinition Tail_3 = Tail_2.addOrReplaceChild("Tail_3", CubeListBuilder.create().texOffs(54, 17).addBox(-1.0F, -1.0F, -0.4F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 3.0F));
        PartDefinition Tail_4 = Tail_3.addOrReplaceChild("Tail_4", CubeListBuilder.create().texOffs(49, 35).addBox(-0.99F, -0.97F, -0.6F, 1.98F, 1.98F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 3.0F));
        Tail_4.addOrReplaceChild("Tail_5", CubeListBuilder.create().texOffs(50, 0).addBox(-0.98F, -0.98F, -0.8F, 1.98F, 1.98F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 3.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(TigerGirlRenderState renderState) {
        super.setupAnim(renderState);
        idleAnimation.apply(renderState.idleAnimationState,     renderState.ageInTicks);
        walkAnimation.apply(renderState.walkAnimationState,     renderState.ageInTicks);
        runAnimation.apply(renderState.runAnimationState,       renderState.ageInTicks);
        attackAnimation.apply(renderState.attackAnimationState, renderState.ageInTicks);
        // yRot is already body-relative (same convention as vanilla VillagerModel).
        this.head.yRot = renderState.yRot * ((float) Math.PI / 180.0f);
        this.head.xRot = renderState.xRot * ((float) Math.PI / 180.0f);
    }
}
