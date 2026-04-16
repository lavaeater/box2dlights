package box2dLight;

import box2dLight.base.BaseLight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.QueryCallback;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.RayCastCallback;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntArray;

/**
 * Light is data container for all the light parameters. When created lights
 * are automatically added to rayHandler and could be removed by calling
 * {@link #remove()} and added manually by calling {@link #add(RayHandler)}.
 * 
 * <p>Implements {@link BaseLight}
 * 
 * @author kalle_h
 */
public abstract class Light extends BaseLight {

	static final float oneColorBits = Color.toFloatBits(1f, 1f, 1f, 1f);

	protected RayHandler rayHandler;

	protected boolean soft = true;
	protected boolean xray = false;
	protected boolean staticLight = false;
	protected boolean culled = false;
	protected boolean dirty = true;
	protected boolean ignoreBody = false;

	protected int rayNum;
	protected int vertexNum;
	
	protected float softShadowLength = 2.5f;
	
	protected Mesh softShadowMesh;

	protected float[] mx;
	protected float[] my;
	protected float[] f;
	protected int m_index = 0;

	/**
	 * Dynamic shadows variables *
	 */
	protected static final LightData tmpData = new LightData(0f);

	protected float pseudo3dHeight = 0f;

	protected final Array<Mesh> dynamicShadowMeshes = new Array<Mesh>();
	//Should never be cleared except when the light changes position (not direction). Prevents shadows from disappearing when fixture is out of sight.
	protected final Array<Fixture> affectedFixtures = new Array<Fixture>();
	protected final Array<Vector2> tmpVerts = new Array<Vector2>();

	protected final IntArray ind = new IntArray();

	protected final Vector2 tmpStart = new Vector2();
	protected final Vector2 tmpEnd = new Vector2();
	protected final Vector2 tmpVec = new Vector2();
	protected final Vector2 center = new Vector2();

	/**
	 * Creates new active light and automatically adds it to the specified
	 * {@link RayHandler} instance.
	 * 
	 * @param rayHandler
	 *            not null instance of RayHandler
	 * @param rays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 * @param color
	 *            light color
	 * @param distance
	 *            light distance (if applicable), soft shadow length is set to distance * 0.1f
	 * @param directionDegree
	 *            direction in degrees (if applicable) 
	 */
	public Light(RayHandler rayHandler, int rays, Color color,
				 float distance, float directionDegree) {
		rayHandler.lightList.add(this);
		this.rayHandler = rayHandler;
		lightHandler = rayHandler;
		setRayNum(rays);
		setColor(color);
		setDistance(distance);
		setSoftnessLength(distance * 0.1f);
		setDirection(directionDegree);
	}

	/**
	 * Updates this light
	 */
	public abstract void update();

	/**
	 * Render this light
	 */
	public abstract void render();

	/**
	 * Render this light shadow
	 */
	public void dynamicShadowRender() {
		for (Mesh m : dynamicShadowMeshes) {
			m.render(rayHandler.lightShader, GL20.GL_TRIANGLE_STRIP);
		}
	}

	/**
	 * Sets light distance
	 *
	 * <p>NOTE: MIN value should be capped to 0.1f meter
	 */
	public abstract void setDistance(float dist);

	/**
	 * Sets light direction
	 */
	public abstract void setDirection(float directionDegree);

	/**
	 * Attaches light to specified body
	 *
	 * @param body
	 *            that will be automatically followed, note that the body
	 *            rotation angle is taken into account for the light offset
	 *            and direction calculations
	 */
	public abstract void attachToBody(Body body);

	/**
	 * @return attached body or {@code null}
	 *
	 * @see #attachToBody(Body)
	 */
	public abstract Body getBody();

	/**
	 * Sets light starting position
	 *
	 * @see #setPosition(Vector2)
	 */
	public abstract void setPosition(float x, float y);

	/**
	 * Sets light starting position
	 *
	 * @see #setPosition(float, float)
	 */
	public abstract void setPosition(Vector2 position);

	/**
	 * @return horizontal starting position of light in world coordinates
	 */
	public abstract float getX();

	/**
	 * @return vertical starting position of light in world coordinates
	 */
	public abstract float getY();

	/**
	 * @return starting position of light in world coordinates
	 *         <p>NOTE: changing this vector does nothing
	 */
	public Vector2 getPosition() {
		return tmpPosition;
	}

	/**
	 * Sets light color
	 *
	 * <p>NOTE: you can also use colorless light with shadows, e.g. (0,0,0,1)
	 *
	 * @param newColor
	 *            RGB set the color and Alpha set intensity
	 *
	 * @see #setColor(float, float, float, float)
	 */
	public void setColor(Color newColor) {
		if (newColor != null) {
			color.set(newColor);
		} else {
			color.set(DefaultColor);
		}
		colorF = color.toFloatBits();
		if (staticLight) dirty = true;
	}

	/**
	 * Sets light color
	 *
	 * <p>NOTE: you can also use colorless light with shadows, e.g. (0,0,0,1)
	 *
	 * @param r
	 *            lights color red component
	 * @param g
	 *            lights color green component
	 * @param b
	 *            lights color blue component
	 * @param a
	 *            lights shadow intensity
	 *
	 * @see #setColor(Color)
	 */
	public void setColor(float r, float g, float b, float a) {
		color.set(r, g, b, a);
		colorF = color.toFloatBits();
		if (staticLight) dirty = true;
	}

	/**
	 * Adds light to specified RayHandler
	 */
	public void add(RayHandler rayHandler) {
		this.rayHandler = rayHandler;
		if (active) {
			rayHandler.lightList.add(this);
		} else {
			rayHandler.disabledLights.add(this);
		}
	}

	/**
	 * Removes light from specified RayHandler and disposes it
	 */
	public void remove() {
		remove(true);
	}

	/**
	 * Removes light from specified RayHandler and disposes it if requested
	 */
	public void remove(boolean doDispose) {
		if (active) {
			rayHandler.lightList.removeValue(this, false);
		} else {
			rayHandler.disabledLights.removeValue(this, false);
		}
		rayHandler = null;
		if (doDispose) dispose();
	}

	/**
	 * Disposes all light resources
	 */
	public void dispose() {
		affectedFixtures.clear();
		lightMesh.dispose();
		softShadowMesh.dispose();
		for (Mesh mesh : dynamicShadowMeshes) {
			mesh.dispose();
		}
		dynamicShadowMeshes.clear();
	}

	/**
	 * Enables/disables softness on tips of this light beams
	 */
	public void setSoft(boolean soft) {
		this.soft = soft;
		if (staticLight) dirty = true;
	}

	/**
	 * @return if tips of this light beams are soft
	 */
	public boolean isSoft() {
		return soft;
	}
	
	/**
	 * Sets softness value for beams tips
	 * 
	 * <p>Default: {@code 2.5f}
	 */
	public void setSoftnessLength(float softShadowLength) {
		this.softShadowLength = softShadowLength;
		if (staticLight) dirty = true;
	}

	/**
	 * @return softness value for beams tips
	 *         <p>Default: {@code 2.5f}
	 */
	public float getSoftShadowLength() {
		return softShadowLength;
	}

	/**
	 * @return direction in degrees (0 if not applicable)
	 */
	public float getDirection(){
		return direction;
	}

	/**
	 * Checks if given point is inside of this light area
	 * 
	 * @param x - horizontal position of point in world coordinates
	 * @param y - vertical position of point in world coordinates
	 */
	public boolean contains(float x, float y) {
		return false;
	}
	
	/**
	 * Sets if the attached body fixtures should be ignored during raycasting
	 * 
	 * @param flag - if {@code true} all the fixtures of attached body
	 *               will be ignored and will not create any shadows for this
	 *               light. By default is set to {@code false}. 
	 */
	public void setIgnoreAttachedBody(boolean flag) {
		ignoreBody = flag;
	}
	
	/**
	 * @return if the attached body fixtures will be ignored during raycasting
	 */
	public boolean getIgnoreAttachedBody() {
		return ignoreBody;
	}

	public void setHeight(float height) {
		this.pseudo3dHeight = height;
	}

	/**
	 * Internal method for mesh update depending on ray number
	 */
	void setRayNum(int rays) {
		if (rays < MIN_RAYS)
			rays = MIN_RAYS;

		rayNum = rays;
		vertexNum = rays + 1;

		segments = new float[vertexNum * 8];
		mx = new float[vertexNum];
		my = new float[vertexNum];
		f = new float[vertexNum];
	}
	
	/** 
	 * @return number of rays set for this light
	 */
	public int getRayNum()
	{
		return rayNum;
	}

	/** Global lights filter **/
	static private Filter globalFilterA = null;
	/** This light specific filter **/
	private Filter filterA = null;

	final RayCastCallback ray = new RayCastCallback() {
		@Override
		final public float reportRayFixture(Fixture fixture, Vector2 point,
				Vector2 normal, float fraction) {
			
			if ((globalFilterA != null) && !globalContactFilter(fixture))
				return -1;
			
			if ((filterA != null) && !contactFilter(fixture))
				return -1;

			if (ignoreBody && fixture.getBody() == getBody())
				return -1;

			// if (fixture.isSensor())
			// return -1;
			mx[m_index] = point.x;
			my[m_index] = point.y;
			f[m_index] = fraction;
			return fraction;
		}
	};
	
	boolean contactFilter(Fixture fixtureB) {
		Filter filterB = fixtureB.getFilterData();

		if (filterA.groupIndex != 0 &&
			filterA.groupIndex == filterB.groupIndex)
			return filterA.groupIndex > 0;

		return  (filterA.maskBits & filterB.categoryBits) != 0 &&
				(filterA.categoryBits & filterB.maskBits) != 0;
	}

	/**
	 * Sets given contact filter for this light
	 */
	public void setContactFilter(Filter filter) {
		filterA = filter;
	}

	/**
	 * Creates new contact filter for this light with given parameters
	 * 
	 * @param categoryBits - see {@link Filter#categoryBits}
	 * @param groupIndex   - see {@link Filter#groupIndex}
	 * @param maskBits     - see {@link Filter#maskBits}
	 */
	public void setContactFilter(short categoryBits, short groupIndex,
			short maskBits) {
		filterA = new Filter();
		filterA.categoryBits = categoryBits;
		filterA.groupIndex = groupIndex;
		filterA.maskBits = maskBits;
	}

	boolean globalContactFilter(Fixture fixtureB) {
		Filter filterB = fixtureB.getFilterData();

		if (globalFilterA.groupIndex != 0 &&
			globalFilterA.groupIndex == filterB.groupIndex)
			return globalFilterA.groupIndex > 0;

		return  (globalFilterA.maskBits & filterB.categoryBits) != 0 &&
				(globalFilterA.categoryBits & filterB.maskBits) != 0;
	}

	/**
	 * Sets given contact filter for ALL LIGHTS
	 */
	static public void setGlobalContactFilter(Filter filter) {
		globalFilterA = filter;
	}

	/**
	 * Creates new contact filter for ALL LIGHTS with give parameters
	 * 
	 * @param categoryBits - see {@link Filter#categoryBits}
	 * @param groupIndex   - see {@link Filter#groupIndex}
	 * @param maskBits     - see {@link Filter#maskBits}
	 */
	static public void setGlobalContactFilter(short categoryBits, short groupIndex,
			short maskBits) {
		globalFilterA = new Filter();
		globalFilterA.categoryBits = categoryBits;
		globalFilterA.groupIndex = groupIndex;
		globalFilterA.maskBits = maskBits;
	}

	protected boolean onDynamicCallback(Fixture fixture) {

		if ((globalFilterA != null) && !globalContactFilter(fixture)) {
			return false;
		}

		if ((filterA != null) && !contactFilter(fixture)) {
			return false;
		}

		if (ignoreBody && fixture.getBody() == getBody()) {
			return false;
		}
		//We only add the affectedFixtures once
		return !affectedFixtures.contains(fixture, true);
	}

	final QueryCallback dynamicShadowCallback = new QueryCallback() {

		@Override
		public boolean reportFixture(Fixture fixture) {
			if (!onDynamicCallback(fixture)) {
				return true;
			}
			affectedFixtures.add(fixture);
			if (fixture.getUserData() instanceof LightData) {
				LightData data = (LightData) fixture.getUserData();
				data.shadowsDropped++;
			}
			return true;
		}

	};

}
