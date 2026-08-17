package dev.lilkuzco.cosmos.client.mixin;

import dev.lilkuzco.cosmos.client.CameraAccess;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Opens {@code Camera.move} to {@link CameraMixin}. */
@Mixin(Camera.class)
public interface CameraAccessMixin extends CameraAccess {

	@Invoker("move")
	@Override
	void cosmos$move(float forward, float up, float left);
}
