package dev.lilkuzco.cosmos.client;

/**
 * Reaches {@code Camera.move}, which is protected.
 *
 * <p>Deliberately OUTSIDE the mixin package. Mixin owns every class under
 * {@code ...client.mixin.*} and refuses to let one be referenced directly — the first version put
 * this interface inside the mixin as a nested type and the client died at load with
 * {@code IllegalClassLoadError ... cannot be referenced directly}. Loud, immediate, and exactly
 * the failure mode a fragile mixin should have.
 */
public interface CameraAccess {
	void cosmos$move(float forward, float up, float left);
}
