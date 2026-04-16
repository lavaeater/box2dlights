package box2dLight;

import box2dLight.base.BaseLight;
import box2dLight.base.BaseLightHandler;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Disposable;

/**
 * Handler that manages everything related to lights updating and rendering
 *
 * <p>Extends {@link BaseLightHandler}
 *
 * @author kalle_h
 */
public class RayHandler extends BaseLightHandler {

	static float gammaCorrectionParameter = 1f;

	/**
	 * TODO: This could be made adaptive to ratio of camera sizes * zoom vs the
	 * CircleShape radius - thus will provide smooth radial shadows while
	 * resizing and zooming in and out
	 */
	static int CIRCLE_APPROX_POINTS = 32;

	static float dynamicShadowColorReduction = 1;

	static int MAX_SHADOW_VERTICES = 64;

	static boolean isDiffuse = false;

	/**
	 * Enables/disables usage of diffuse algorithm
	 *
	 * <p>If set to true lights are blended using the diffuse shader. This is
	 * more realistic model than normally used as it preserve colors but might
	 * look bit darker and also it might improve performance slightly.
	 */
	public void setDiffuseLight(boolean useDiffuse) {
		isDiffuse = useDiffuse;
		lightMap.createShaders();
	}

	/**
	 * @return if the usage of diffuse algorithm is enabled
	 *
	 * <p>If set to true lights are blended using the diffuse shader. This is
	 * more realistic model than normally used as it preserve colors but might
	 * look bit darker and also it might improve performance slightly.
	 */
	public static boolean isDiffuseLight() {
		return isDiffuse;
	}

	/** Typed shadow of BaseLightHandler.lightMap for LightMap-specific access **/
	LightMap lightMap;
	ShaderProgram customLightShader = null;

	/** Experimental pseudo-3d mode **/
	boolean pseudo3d = false;
	boolean shadowColorInterpolation = false;

	/** Shadows BaseLightHandler.lightBlurPasses for direct field access **/
	int blurNum = 1;
	/** Shadows BaseLightHandler.lightsRenderedLastFrame **/
	int lightRenderedLastFrame = 0;

	/**
	 * Class constructor specifying the physics world from where collision
	 * geometry is taken.
	 *
	 * <p>NOTE: FBO size is 1/4 * screen size and used by default.
	 *
	 * Default setting are:
	 * <ul>
	 *     <li>culling = true
	 *     <li>shadows = true
	 *     <li>diffuse = false
	 *     <li>blur = true
	 *     <li>blurNum = 1
	 *     <li>ambientLight = 0f
	 * </ul>
	 *
	 * @see #RayHandler(World, int, int, RayHandlerOptions)
	 */
	public RayHandler(World world) {
		this(world, Gdx.graphics.getWidth() / 4, Gdx.graphics
				.getHeight() / 4, null);
	}

	public RayHandler(World world, RayHandlerOptions options) {
		this(world, Gdx.graphics.getWidth() / 4, Gdx.graphics
				.getHeight() / 4, options);
	}

	/**
	 * Class constructor specifying the physics world from where collision
	 * geometry is taken, and size of FBO used for intermediate rendering.
	 *
	 * @see #RayHandler(World)
	 */
	public RayHandler(World world, int fboWidth, int fboHeight) {
		this(world, fboWidth, fboHeight, null);
	}

	public RayHandler(World world, int fboWidth, int fboHeight, RayHandlerOptions options) {
		super(world);

		if (options != null) {
			isDiffuse = options.isDiffuse;
			gammaCorrection = options.gammaCorrection;
			pseudo3d = options.pseudo3d;
			shadowColorInterpolation = options.shadowColorInterpolation;
		}

		resizeFBO(fboWidth, fboHeight);
	}

	/**
	 * Resize the FBO used for intermediate rendering.
	 */
	public void resizeFBO(int fboWidth, int fboHeight) {
		if (lightMap != null) {
			lightMap.dispose();
		}
		lightMap = new LightMap(this, fboWidth, fboHeight);
	}

	/**
	 * Prepare all lights for rendering.
	 *
	 * <p>You should need to use this method only if you want to render lights
	 * on a frame buffer object. Use {@link #render()} otherwise.
	 *
	 * <p><b>NOTE!</b> Don't call this inside of any begin/end statements.
	 *
	 * @see #renderOnly()
	 * @see #render()
	 */
	public void prepareRender() {
		lightRenderedLastFrame = 0;

		Gdx.gl.glDepthMask(false);
		Gdx.gl.glEnable(GL20.GL_BLEND);

		boolean useLightMap = (shadows || blur);
		if (useLightMap) {
			lightMap.frameBuffer.begin();
			Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		}

		simpleBlendFunc.apply();

		ShaderProgram shader = customLightShader != null ? customLightShader : lightShader;
		shader.bind();
		{
			shader.setUniformMatrix("u_projTrans", combined);
			if (customLightShader != null) updateLightShader();

			for (BaseLight bl : lightList) {
				Light light = (Light) bl;
				if (customLightShader != null) updateLightShaderPerLight(light);
				light.render();
			}
		}

		if (useLightMap) {
			if (customViewport) {
				lightMap.frameBuffer.end(
					viewportX,
					viewportY,
					viewportWidth,
					viewportHeight);
			} else {
				lightMap.frameBuffer.end();
			}
		}

		if (useLightMap && pseudo3d) {
			lightMap.shadowBuffer.begin();
			Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

			for (BaseLight bl : lightList) {
				((Light) bl).dynamicShadowRender();
			}

			if (customViewport) {
				lightMap.shadowBuffer.end(
						viewportX,
						viewportY,
						viewportWidth,
						viewportHeight);
			} else {
				lightMap.shadowBuffer.end();
			}
		}

		boolean needed = lightRenderedLastFrame > 0;
		// this way lot less binding
		if (needed && blur)
			lightMap.gaussianBlur(lightMap.frameBuffer, blurNum);
		if (needed && blur && pseudo3d)
			lightMap.gaussianBlur(lightMap.shadowBuffer, blurNum);
	}

	/**
	 * Manual rendering method for all lights.
	 *
	 * <p><b>NOTE!</b> Remember to set combined matrix and update lights
	 * before using this method manually.
	 *
	 * <p>Don't call this inside of any begin/end statements.
	 * Call this method after you have rendered background but before UI.
	 * Box2d bodies can be rendered before or after depending how you want
	 * the x-ray lights to interact with them.
	 *
	 * @see #updateAndRender()
	 * @see #update()
	 * @see #setCombinedMatrix(Matrix4)
	 * @see #setCombinedMatrix(Matrix4, float, float, float, float)
	 */
	@Override
	public void render() {
		prepareRender();
		lightMap.render();
	}

	/**
	 * Manual rendering method for all lights that can be used inside of
	 * begin/end statements
	 *
	 * <p>Use this method if you want to render lights in a frame buffer
	 * object. You must call {@link #prepareRender()} before calling this
	 * method. Also, {@link #prepareRender()} must not be inside of any
	 * begin/end statements
	 *
	 * @see #prepareRender()
	 */
	public void renderOnly() {
		lightMap.render();
	}

	/**
	 * Called before light rendering start
	 *
	 * Override this if you are using custom light shader
	 */
	protected void updateLightShader() {
	}

	/**
	 * Called for custom light shader before each light is rendered
	 *
	 * Override this if you are using custom light shader
	 */
	protected void updateLightShaderPerLight(Light light) {
	}

	/**
	 * Disposes all this rayHandler lights and resources
	 */
	@Override
	public void dispose() {
		super.dispose();
		if (lightMap != null) lightMap.dispose();
	}

	/**
	 * Set custom light shader, null to reset to default
	 *
	 * Changes will take effect next time #render() is called
	 */
	public void setLightShader(ShaderProgram customLightShader) {
		this.customLightShader = customLightShader;
	}

	@Override
	public void setBlurNum(int blurNum) {
		this.blurNum = blurNum;
		super.setBlurNum(blurNum);
	}

	/**
	 * @return if gamma correction is enabled or not
	 */
	public static boolean getGammaCorrection() {
		return gammaCorrection;
	}

	/**
	 * Enables/disables gamma correction.
	 *
	 * <p><b>This need to be done before creating instance of rayHandler.</b>
	 *
	 * <p>NOTE: To match the visuals with gamma uncorrected lights the light
	 * distance parameters is modified implicitly.
	 */
	public void applyGammaCorrection(boolean gammaCorrectionWanted) {
		gammaCorrection = gammaCorrectionWanted;
		gammaCorrectionParameter = gammaCorrection ? GAMMA_COR : 1f;
		lightMap.createShaders();
	}

	public static float getDynamicShadowColorReduction() {
		return dynamicShadowColorReduction;
	}

	/**
	 * Static setters are deprecated, use {@link RayHandlerOptions}
	 */
	@Deprecated
	public static void useDiffuseLight(boolean useDiffuse) {
	}

	/**
	 * Static setters are deprecated, use {@link RayHandlerOptions}
	 */
	@Deprecated
	public static void setGammaCorrection(boolean gammaCorrectionWanted) {
	}

	/**
	 * /!\ Experimental mode with dynamic shadowing in pseudo-3d world
	 *
	 * @param flag enable pseudo 3d effect
	 */
	public void setPseudo3dLight(boolean flag) {
		setPseudo3dLight(flag, false);
	}

	/**
	 * /!\ Experimental mode with dynamic shadowing in pseudo-3d world
	 *
	 * @param flag enable pseudo 3d effect
	 * @param interpolateShadows interpolate shadow color
	 */
	public void setPseudo3dLight(boolean flag, boolean interpolateShadows) {
		pseudo3d = flag;
		shadowColorInterpolation = interpolateShadows;

		lightMap.createShaders();
	}

}
