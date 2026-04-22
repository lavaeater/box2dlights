package box2dLight.p3d;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Mesh.VertexDataType;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.Shape.Type;

/**
 * Light shaped as a circle's sector with given radius, direction and angle,
 * with pseudo-3D height-based shadow support.
 *
 * <p>Extends {@link P3dPositionalLight}
 *
 * @author rinold
 */
public class P3dConeLight extends P3dPositionalLight {

	protected float coneDegree;

	/**
	 * Creates light shaped as a circle's sector with given radius, direction and arc angle
	 *
	 * @param lightManager
	 *            not {@code null} instance of P3dLightManager
	 * @param rays
	 *            number of rays - more rays make light to look more realistic
	 *            but will decrease performance, can't be less than MIN_RAYS
	 * @param color
	 *            color, set to {@code null} to use the default color
	 * @param distance
	 *            distance of cone light
	 * @param x
	 *            horizontal position in world coordinates
	 * @param y
	 *            vertical position in world coordinates
	 * @param directionDegree
	 *            direction of cone light in degrees
	 * @param coneDegree
	 *            half-size of cone light, centered over direction (clamped 0-180)
	 */
	public P3dConeLight(P3dLightManager lightManager, int rays, Color color,
			float distance, float x, float y, float directionDegree,
			float coneDegree) {
		super(lightManager, rays, color, distance, x, y, directionDegree);
		setConeDegree(coneDegree);
	}

	@Override
	public void update() {
		updateBody();
		if (dirty) {
			setEndPoints();
		}

		if (cull()) return;
		if (staticLight && !dirty) return;

		dirty = false;
		updateMesh();
		prepeareFixtureData();
		updateDynamicShadowMeshes();
	}

	@Override
	public void dynamicShadowRender() {
		for (int i = 0; i < activeShadows; i++) {
			Mesh m = dynamicShadowMeshes.get(i);
			m.render(shader, GL20.GL_TRIANGLE_STRIP);
		}
	}

	/**
	 * Sets light direction
	 * <p>Actual recalculations will be done only on {@link #update()} call
	 */
	@Override
	public void setDirection(float directionDegree) {
		direction = directionDegree;
		dirty = true;
	}

	public float getDirection() {
		return direction;
	}

	/**
	 * @return this light's cone degree (half-angle)
	 */
	public float getConeDegree() {
		return coneDegree;
	}

	/**
	 * Sets the half-angle of the cone arc.
	 *
	 * <p>Arc angle = coneDegree * 2, centered over direction angle
	 * <p>Actual recalculations will be done only on {@link #update()} call
	 */
	public void setConeDegree(float coneDegree) {
		this.coneDegree = MathUtils.clamp(coneDegree, 0f, 180f);
		dirty = true;
	}

	/**
	 * Sets light distance
	 *
	 * <p>MIN value capped to 0.01f meter
	 * <p>Actual recalculations will be done only on {@link #update()} call
	 */
	@Override
	public void setDistance(float dist) {
		dist *= gammaCorrectionValue;
		this.distance = dist < 0.01f ? 0.01f : dist;
		dirty = true;
	}

	/** Updates light sector based on distance, direction and coneDegree **/
	protected void setEndPoints() {
		for (int i = 0; i < rayNum; i++) {
			float angle = direction + coneDegree - 2f * coneDegree * i / (rayNum - 1f);
			sin[i] = MathUtils.sinDeg(angle);
			cos[i] = MathUtils.cosDeg(angle);
			endX[i] = distance * cos[i];
			endY[i] = distance * sin[i];
		}
	}

	protected void updateDynamicShadowMeshes() {
		activeShadows = 0;
		for (Fixture fixture : affectedFixtures) {
			P3dData data = (P3dData) fixture.getUserData();
			if (data == null || fixture.isSensor()) continue;

			int size = 0;
			float l = 0f;

			Shape fixtureShape = fixture.getShape();
			Type type = fixtureShape.getType();
			Body body = fixture.getBody();
			center.set(body.getWorldCenter());

			if (type == Type.Polygon || type == Type.Chain) {
				boolean isPolygon = (type == Type.Polygon);
				ChainShape cShape = isPolygon ? null : (ChainShape) fixtureShape;
				PolygonShape pShape = isPolygon ? (PolygonShape) fixtureShape : null;
				int vertexCount = isPolygon ? pShape.getVertexCount() : cShape.getVertexCount();
				int minN = -1;
				int maxN = -1;
				int minDstN = -1;
				float minDst = Float.POSITIVE_INFINITY;
				boolean hasGasp = false;
				tmpVerts.clear();
				for (int n = 0; n < vertexCount; n++) {
					if (isPolygon) {
						pShape.getVertex(n, tmpVec);
					} else {
						cShape.getVertex(n, tmpVec);
					}
					tmpVec.set(body.getWorldPoint(tmpVec));
					tmpVerts.add(tmpVec.cpy());
					tmpEnd.set(tmpVec).sub(start).limit2(0.0001f).add(tmpVec);
					if (fixture.testPoint(tmpEnd)) {
						if (minN == -1) minN = n;
						maxN = n;
						hasGasp = true;
						continue;
					}

					float currDist = tmpVec.dst2(start);
					if (currDist < minDst) {
						minDst = currDist;
						minDstN = n;
					}
				}

				ind.clear();
				if (!hasGasp) {
					tmpVec.set(tmpVerts.get(minDstN));
					for (int n = minDstN; n < vertexCount; n++) {
						ind.add(n);
					}
					for (int n = 0; n < minDstN; n++) {
						ind.add(n);
					}
					if (Intersector.pointLineSide(start, center, tmpVec) > 0) {
						ind.reverse();
						ind.insert(0, ind.pop());
					}
				} else if (minN == 0 && maxN == vertexCount - 1) {
					for (int n = maxN - 1; n > minN; n--) {
						ind.add(n);
					}
				} else {
					for (int n = minN - 1; n > -1; n--) {
						ind.add(n);
					}
					for (int n = vertexCount - 1; n > maxN; n--) {
						ind.add(n);
					}
				}

				for (int n : ind.toArray()) {
					tmpVec.set(tmpVerts.get(n));

					float dst = tmpVec.dst(start);
					l = data.getLimit(dst, height, distance);
					tmpEnd.set(tmpVec).sub(start).setLength(l).add(tmpVec);
					float f1 = 1f - dst / distance;
					float f2 = 1f - (dst + l) / distance;

					segments[size++] = tmpVec.x;
					segments[size++] = tmpVec.y;
					segments[size++] = colorF;
					segments[size++] = f1;

					segments[size++] = tmpEnd.x;
					segments[size++] = tmpEnd.y;
					segments[size++] = colorF;
					segments[size++] = f2;
				}
			} else if (type == Type.Circle) {
				CircleShape shape = (CircleShape) fixtureShape;
				float r = shape.getRadius();
				float dst = tmpVec.set(center).dst(start);
				float a = (float) Math.acos(r / dst);
				l = data.getLimit(dst, height, distance);
				float f1 = 1f - dst / distance;
				float f2 = 1f - (dst + l) / distance;

				tmpVec.set(start).sub(center).clamp(r, r).rotateRad(a);
				tmpStart.set(center).add(tmpVec);

				float angle = (MathUtils.PI2 - 2f * a) /
						P3dLightManager.CIRCLE_APPROX_POINTS;
				for (int k = 0; k < P3dLightManager.CIRCLE_APPROX_POINTS; k++) {
					tmpStart.set(center).add(tmpVec);
					segments[size++] = tmpStart.x;
					segments[size++] = tmpStart.y;
					segments[size++] = colorF;
					segments[size++] = f1;

					tmpEnd.set(tmpStart).sub(start).setLength(l).add(tmpStart);
					segments[size++] = tmpEnd.x;
					segments[size++] = tmpEnd.y;
					segments[size++] = colorF;
					segments[size++] = f2;

					tmpVec.rotateRad(angle);
				}
			} else if (type == Type.Edge) {
				EdgeShape shape = (EdgeShape) fixtureShape;

				shape.getVertex1(tmpVec);
				tmpVec.set(body.getWorldPoint(tmpVec));
				float dst = tmpVec.dst(start);
				l = data.getLimit(dst, height, distance);
				float f1 = 1f - dst / distance;
				float f2 = 1f - (dst + l) / distance;

				segments[size++] = tmpVec.x;
				segments[size++] = tmpVec.y;
				segments[size++] = colorF;
				segments[size++] = f1;

				tmpEnd.set(tmpVec).sub(start).setLength(l).add(tmpVec);
				segments[size++] = tmpEnd.x;
				segments[size++] = tmpEnd.y;
				segments[size++] = colorF;
				segments[size++] = f2;

				shape.getVertex2(tmpVec);
				tmpVec.set(body.getWorldPoint(tmpVec));
				dst = tmpVec.dst(start);
				l = data.getLimit(dst, height, distance);
				f1 = 1f - dst / distance;
				f2 = 1f - (dst + l) / distance;

				segments[size++] = tmpVec.x;
				segments[size++] = tmpVec.y;
				segments[size++] = colorF;
				segments[size++] = f1;

				tmpEnd.set(tmpVec).sub(start).setLength(l).add(tmpVec);
				segments[size++] = tmpEnd.x;
				segments[size++] = tmpEnd.y;
				segments[size++] = colorF;
				segments[size++] = f2;
			}

			Mesh mesh;
			if (activeShadows >= dynamicShadowMeshes.size) {
				mesh = new Mesh(
						VertexDataType.VertexArray, false,
						P3dLightManager.MAX_SHADOW_VERTICES, 0,
						new VertexAttribute(Usage.Position, 2, "vertex_positions"),
						new VertexAttribute(Usage.ColorPacked, 4, "quad_colors"),
						new VertexAttribute(Usage.Generic, 1, "s"));
				dynamicShadowMeshes.add(mesh);
			} else {
				mesh = dynamicShadowMeshes.get(activeShadows);
			}
			mesh.setVertices(segments, 0, size);
			activeShadows++;
		}
	}

}
