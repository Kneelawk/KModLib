package com.kneelawk.kmodlib.client.blockmodel.ct;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;

import net.minecraft.block.BlockState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import com.kneelawk.kmodlib.client.blockmodel.BakedModelLayer;
import com.kneelawk.kmodlib.client.blockmodel.connector.ModelConnector;
import com.kneelawk.kmodlib.client.blockmodel.sprite.BakedSpriteSupplier;
import com.kneelawk.kmodlib.client.blockmodel.util.CubeModelUtils;
import com.kneelawk.kmodlib.client.blockmodel.util.FacePos;

import static com.kneelawk.kmodlib.client.blockmodel.util.TexDirectionUtils.texDown;
import static com.kneelawk.kmodlib.client.blockmodel.util.TexDirectionUtils.texLeft;
import static com.kneelawk.kmodlib.client.blockmodel.util.TexDirectionUtils.texRight;
import static com.kneelawk.kmodlib.client.blockmodel.util.TexDirectionUtils.texUp;
import static java.lang.Math.min;
import static net.minecraft.util.math.MathHelper.clamp;

/**
 * Connected-texture baked model layer.
 */
public class BakedCTLayer implements BakedModelLayer {
    private final BakedSpriteSupplier[] sprites;
    private final RenderMaterial material;
    private final float depth;
    private final boolean cullFaces;
    private final boolean interiorBorder;
    private final int tintIndex;
    private final ModelConnector connector;

    private final FacePos[] corners;
    private final boolean doCorners;

    /**
     * Constructs a connected-texture model layer.
     *
     * @param sprites        sprite suppliers in order: exterior corners, horizontal edges, vertical edges, interior
     *                       corners, and optionally, no edges.
     * @param material       the material to render with.
     * @param depth          the depth into the block to render at.
     * @param cullFaces      whether to cull faces.
     * @param interiorBorder whether to have borders inside a corner between three blocks in an L shape.
     * @param tintIndex      the tint index to render with.
     * @param connector      the connector to control which blocks to connect to.
     */
    public BakedCTLayer(@Nullable BakedSpriteSupplier[] sprites, RenderMaterial material, float depth,
                        boolean cullFaces, boolean interiorBorder, int tintIndex, ModelConnector connector) {
        this.sprites = sprites;
        this.material = material;
        this.depth = depth;
        float depthClamped = clamp(depth, 0.0f, 0.5f);
        float depthMaxed = min(depth, 0.5f);
        this.cullFaces = cullFaces;
        this.interiorBorder = interiorBorder;
        this.tintIndex = tintIndex;
        this.connector = connector;

        corners = new FacePos[]{
            new FacePos(0.0f + depthClamped, 0.0f + depthClamped, 0.5f, 0.5f, depthMaxed),
            new FacePos(0.5f, 0.0f + depthClamped, 1.0f - depthClamped, 0.5f, depthMaxed),
            new FacePos(0.0f + depthClamped, 0.5f, 0.5f, 1.0f - depthClamped, depthMaxed),
            new FacePos(0.5f, 0.5f, 1.0f - depthClamped, 1.0f - depthClamped, depthMaxed)
        };

        doCorners = sprites.length > 4;
    }

    @Override
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos,
                               Supplier<Random> randomSupplier, RenderContext context) {
        QuadEmitter emitter = context.getEmitter();

        for (Direction normal : Direction.values()) {
            int indices = getIndices(blockView, state, pos, normal);

            for (int corner = 0; corner < 4; corner++) {
                BakedSpriteSupplier supplier = sprites[(indices >> (corner * 3)) & 0x7];
                if (supplier == null) continue;

                Sprite sprite = supplier.getBlockSprite(blockView, state, pos, randomSupplier, normal);
                if (sprite == null) continue;

                corners[corner].emit(emitter, normal, null);
                emitter.spriteBake(sprite, MutableQuadView.BAKE_NORMALIZED);
                emitter.colorIndex(tintIndex);
                emitter.color(-1, -1, -1, -1);
                emitter.material(material);
                emitter.cullFace(cullFaces ? normal : null);
                emitter.emit();
            }
        }
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
        Sprite sprite = sprites[0].getItemSprite(stack, randomSupplier);

        CubeModelUtils.emitCube(context.getEmitter(), null, cullFaces, true, depth, depth,
            material, new Sprite[]{sprite, sprite, sprite, sprite, sprite, sprite},
            new int[]{tintIndex, tintIndex, tintIndex, tintIndex, tintIndex, tintIndex});
    }

    private int getIndices(BlockRenderView view, BlockState state, BlockPos pos, Direction normal) {
        int horizontals = getHorizontals(view, state, pos, normal);
        int verticals = getVerticals(view, state, pos, normal);
        int corners;
        if (doCorners) corners = getCorners(view, state, pos, normal) & horizontals & verticals;
        else corners = 0;

        return (corners << 2) | (horizontals ^ corners) | ((verticals ^ corners) << 1);
    }

    private int getHorizontals(BlockRenderView view, BlockState state, BlockPos pos, Direction normal) {
        boolean right = canConnect(view, state, pos, normal, pos.offset(texRight(normal)));
        boolean left = canConnect(view, state, pos, normal, pos.offset(texLeft(normal)));

        return (left ? 0x41 : 0) | (right ? 0x208 : 0);
    }

    private int getVerticals(BlockRenderView view, BlockState state, BlockPos pos, Direction normal) {
        boolean up = canConnect(view, state, pos, normal, pos.offset(texUp(normal)));
        boolean down = canConnect(view, state, pos, normal, pos.offset(texDown(normal)));

        return (down ? 0x9 : 0) | (up ? 0x240 : 0);
    }

    private int getCorners(BlockRenderView view, BlockState state, BlockPos pos, Direction normal) {
        boolean bl = canConnect(view, state, pos, normal, pos.offset(texDown(normal)).offset(texLeft(normal)));
        boolean br = canConnect(view, state, pos, normal, pos.offset(texDown(normal)).offset(texRight(normal)));
        boolean tl = canConnect(view, state, pos, normal, pos.offset(texUp(normal)).offset(texLeft(normal)));
        boolean tr = canConnect(view, state, pos, normal, pos.offset(texUp(normal)).offset(texRight(normal)));

        return (bl ? 0x1 : 0) | (br ? 0x8 : 0) | (tl ? 0x40 : 0) | (tr ? 0x200 : 0);
    }

    private boolean canConnect(BlockRenderView view, BlockState state, BlockPos pos, Direction normal,
                               BlockPos offsetPos) {
        BlockPos outPos = offsetPos.offset(normal);
        BlockState offsetState = view.getBlockState(offsetPos);
        BlockState outState = view.getBlockState(outPos);
        return connector.canConnect(view, pos, offsetPos, normal, state, offsetState) &&
            (!interiorBorder || !connector.canConnect(view, pos, outPos, normal, state, outState));
    }
}
