package com.simpleautomation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simpleautomation.pipe.PipeBlock;
import com.simpleautomation.pipe.PipeBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dessine l'icône du filtre en petit sur les quatre faces extérieures de l'anneau du connecteur.
 * Un renderer plutôt que des états de bloc : avec 6 côtés, les combinaisons d'états exploseraient.
 */
public class PipeFilterRenderer implements BlockEntityRenderer<PipeBlockEntity, PipeFilterRenderer.State> {
    /** Distance de la face extérieure de l'anneau à l'axe du bras (l'anneau fait 8 px de large). */
    private static final float RING_RADIUS = 4.0F / 16.0F + 0.004F;
    /** Milieu de l'icône le long du bras, depuis le centre du bloc (l'anneau va de 6 à 8 px). */
    private static final float ALONG = 6.0F / 16.0F;
    private static final float SCALE = 0.25F;

    private final ItemModelResolver itemModelResolver;

    public PipeFilterRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    public static class State extends BlockEntityRenderState {
        /** Par direction : l'icône à dessiner, ou null. */
        public final @Nullable ItemStackRenderState[] filters = new ItemStackRenderState[6];
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PipeBlockEntity pipe, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(pipe, state, partialTick, camera, breakProgress);
        int seed = (int) pipe.getBlockPos().asLong();
        boolean isPipe = state.blockState.getBlock() instanceof PipeBlock;
        for (Direction d : Direction.values()) {
            ItemStack stack = pipe.filterStack(d);
            if (stack.isEmpty() || !isPipe || !PipeBlock.side(state.blockState, d).isContainer()) {
                state.filters[d.ordinal()] = null;
                continue;
            }
            ItemStackRenderState item = state.filters[d.ordinal()];
            if (item == null) item = state.filters[d.ordinal()] = new ItemStackRenderState();
            itemModelResolver.updateForTopItem(item, stack, ItemDisplayContext.FIXED, pipe.getLevel(), null, seed + d.ordinal());
        }
    }

    /** La plupart des pipes n'ont aucun filtre : on ne les traite même pas. */
    @Override
    public boolean shouldRender(PipeBlockEntity pipe, Vec3 camera) {
        return pipe.hasFilters() && BlockEntityRenderer.super.shouldRender(pipe, camera);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (Direction d : Direction.values()) {
            ItemStackRenderState item = state.filters[d.ordinal()];
            if (item == null || item.isEmpty()) continue;
            pose.pushPose();
            pose.translate(0.5F, 0.5F, 0.5F);
            pose.mulPose(d.getRotation()); // l'axe Y local pointe maintenant vers le conteneur
            for (int k = 0; k < 4; k++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(90.0F * k));
                pose.translate(0.0F, ALONG, RING_RADIUS);
                pose.scale(SCALE, SCALE, SCALE);
                item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
            pose.popPose();
        }
    }
}
