package box2dLight;

import box2dLight.base.BaseLightMap;
import shaders.*;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

class LightMap extends BaseLightMap {

	RayHandler rayHandler;

	ShaderProgram withoutShadowShader;
	ShaderProgram shadowShader;
	ShaderProgram diffuseShader;

	FrameBuffer shadowBuffer;

	boolean lightMapDrawingDisabled;

	private final int fboWidth, fboHeight;

	public LightMap(RayHandler rayHandler, int fboWidth, int fboHeight) {
		super(rayHandler, fboWidth, fboHeight);
		this.rayHandler = rayHandler;

		if (fboWidth <= 0) fboWidth = 1;
		if (fboHeight <= 0) fboHeight = 1;
		this.fboWidth = fboWidth;
		this.fboHeight = fboHeight;

		shadowBuffer = new FrameBuffer(Format.RGBA8888, fboWidth,
				fboHeight, false);

		createShaders();
	}

	@Override
	public void render() {
		boolean needed = rayHandler.lightRenderedLastFrame > 0;

		if (lightMapDrawingDisabled)
			return;

		if (rayHandler.pseudo3d) {
			frameBuffer.getColorBufferTexture().bind(1);
			shadowBuffer.getColorBufferTexture().bind(0);
		} else {
			// this way lot less binding
			if (needed && rayHandler.isBlur())
				gaussianBlur(frameBuffer, rayHandler.getBlurNum());

			frameBuffer.getColorBufferTexture().bind(0);
		}

		// at last lights are rendered over scene
		if (rayHandler.shadows) {
			final Color c = rayHandler.ambientLight;
			ShaderProgram shader = shadowShader;
			if (rayHandler.pseudo3d) {
				shader.bind();
				if (RayHandler.isDiffuse) {
					rayHandler.diffuseBlendFunc.apply();
					shader.setUniformf("ambient", c.r, c.g, c.b, c.a);
				} else {
					rayHandler.shadowBlendFunc.apply();
					shader.setUniformf("ambient", c.r * c.a, c.g * c.a,
							c.b * c.a, 1f - c.a);
				}
				shader.setUniformi("isDiffuse", RayHandler.isDiffuse ? 1 : 0);
				shader.setUniformi("u_texture", 1);
				shader.setUniformi("u_shadows", 0);
			} else if (RayHandler.isDiffuse) {
				shader = diffuseShader;
				shader.bind();
				rayHandler.diffuseBlendFunc.apply();
				shader.setUniformf("ambient", c.r, c.g, c.b, c.a);
			} else {
				shader.bind();
				rayHandler.shadowBlendFunc.apply();
				shader.setUniformf("ambient", c.r * c.a, c.g * c.a,
						c.b * c.a, 1f - c.a);
			}

			lightMapMesh.render(shader, GL20.GL_TRIANGLE_FAN);
		} else if (needed) {
			rayHandler.simpleBlendFunc.apply();
			withoutShadowShader.bind();

			lightMapMesh.render(withoutShadowShader, GL20.GL_TRIANGLE_FAN);
		}

		Gdx.gl20.glDisable(GL20.GL_BLEND);
	}

	@Override
	public void dispose() {
		disposeShaders();
		if (shadowBuffer != null) shadowBuffer.dispose();
		super.dispose();
	}

	void createShaders() {
		disposeShaders();

		shadowShader = rayHandler.pseudo3d
				? DynamicShadowShader.createShadowShader()
				: ShadowShader.createShadowShader();
		diffuseShader = DiffuseShader.createShadowShader();
		withoutShadowShader = WithoutShadowShader.createShadowShader();
	}

	private void disposeShaders() {
		if (shadowShader != null) shadowShader.dispose();
		if (diffuseShader != null) diffuseShader.dispose();
		if (withoutShadowShader != null) withoutShadowShader.dispose();
	}

}
