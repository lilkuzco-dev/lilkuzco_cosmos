package dev.lilkuzco.cosmos.client.mixin;

import dev.lilkuzco.cosmos.client.CameraShake;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shakes the camera near a burning rocket.
 *
 * <p><b>The empire's first mixin, and it exists because Fabric has no camera hook.</b> Every other
 * client hook cosmos needs — renderers, model layers, screens, payloads — has an API. This one does
 * not: `fabric-rendering-v1` exposes nothing that can move a camera, so the choice was a mixin or
 * no shake at all.
 *
 * <p>Deliberately the smallest possible one. It injects at the tail of {@link Camera#update} and
 * calls the camera's own {@code move}, which is a positional nudge rather than a rotation — so it
 * needs no accessors, no field shadows and no assumptions about how rotation is stored. There is
 * nothing here to break except the method name.
 *
 * <p><b>WATCHLIST: this is version-fragile.</b> `Camera.update(DeltaTracker)` is an obfuscated
 * internal that Mojang may rename or resignature in any release. If it moves, the mixin fails to
 * apply and the client refuses to start — loudly, which is the good failure. Check it on every
 * Minecraft version bump; the shake is cosmetic, so removing this file is always a valid fix.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

	@Inject(method = "update", at = @At("TAIL"))
	private void cosmos$shake(DeltaTracker delta, CallbackInfo info) {
		float amplitude = CameraShake.tick(Minecraft.getInstance());
		if (amplitude == 0.0F) return;
		Camera camera = (Camera) (Object) this;
		// A nudge up and sideways, not a rotation: the horizon stays level and the world jitters,
		// which is what standing near something enormous actually feels like.
		// These factors are MEASURED, not chosen. At 0.06/0.05 the horizon moved one pixel
		// between consecutive frames of a stationary camera - indistinguishable from noise, and
		// unshippable under a ruling that says the screenshots have to prove it fired. At 0.6/0.5
		// it swung ten pixels, which is closer to a car crash than a launch. 0.25/0.20 lands
		// around four, which reads as a rumble.
		((dev.lilkuzco.cosmos.client.CameraAccess) camera)
				.cosmos$move(0.0F, amplitude * 0.25F, amplitude * 0.20F);
	}
}
