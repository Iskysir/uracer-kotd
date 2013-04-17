
package com.bitfire.uracer.game.logic.post.ssao;

import java.nio.ByteBuffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.bitfire.postprocessing.PostProcessorEffect;
import com.bitfire.postprocessing.filters.Blur;
import com.bitfire.postprocessing.filters.Blur.BlurType;
import com.bitfire.postprocessing.utils.FullscreenQuad;
import com.bitfire.postprocessing.utils.PingPongBuffer;
import com.bitfire.uracer.events.GameRendererEvent;
import com.bitfire.uracer.events.GameRendererEvent.Order;
import com.bitfire.uracer.game.GameEvents;
import com.bitfire.uracer.utils.Convert;
import com.bitfire.uracer.utils.ScaleUtils;
import com.bitfire.utils.ShaderLoader;

public final class Ssao extends PostProcessorEffect {

	public enum Quality {
		High(1), Medium(0.75f), Low(0.5f);
		public final float scale;

		Quality (float scale) {
			this.scale = scale;
		}
	}

	private final PingPongBuffer occlusionMap;
	private Texture normalDepthMap;
	private final ShaderProgram shMix, shSsao;
	private final FullscreenQuad quad = new FullscreenQuad();
	private final Texture randomField;
	private Blur blur;
	private boolean nightMode;

	private Matrix3 mtxRot = new Matrix3();
	private Matrix3 invRot = new Matrix3();
	private Matrix4 invPrj = new Matrix4();

	private GameRendererEvent.Listener gameRendererEvent = new GameRendererEvent.Listener() {
		@Override
		public void gameRendererEvent (GameRendererEvent.Type type, Order order) {
			debug(GameEvents.gameRenderer.batch);
		}
	};

	public Ssao (int fboWidth, int fboHeight, Quality quality, boolean nightMode) {
		this.nightMode = nightMode;

		Gdx.app.log("SsaoProcessor", "Quality profile = " + quality.toString());
		float oscale = quality.scale;

		// maps
		occlusionMap = new PingPongBuffer((int)((float)fboWidth * oscale), (int)((float)fboHeight * oscale), Format.RGBA8888, false);

		// shaders
		shMix = ShaderLoader.fromFile("screenspace", "ssao/mix");
		shSsao = ShaderLoader.fromFile("ssao/ssao", "ssao/ssao");

		// blur
		blur = new Blur(occlusionMap.width, occlusionMap.height);

		// compute random field for the ssao shader
		int width = 16;
		int height = 16;
		Format format = Format.RGBA8888;
		randomField = new Texture(width, height, format);
		randomField.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
		randomField.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);

		Pixmap pixels = new Pixmap(width, height, format);
		ByteBuffer bytes = pixels.getPixels();
		int wrote = 0;

		while (wrote < width * height * 4) {
			float x = (MathUtils.random() - 0.5f) * 2.0f;
			float y = (MathUtils.random() - 0.5f) * 2.0f;
			float z = (MathUtils.random() - 0.5f) * 2.0f;
			float l = (float)Math.sqrt(x * x + y * y + z * z);
			if (l <= 1.0f && l > 0.1f) {
				x = (x + 1.0f) * 0.5f;
				y = (y + 1.0f) * 0.5f;
				z = (z + 1.0f) * 0.5f;
				bytes.put((byte)(x * 255f));
				bytes.put((byte)(y * 255f));
				bytes.put((byte)(z * 255f));
				bytes.put((byte)255);
				wrote += 4;
			}
		}

		bytes.flip();
		randomField.draw(pixels, 0, 0);
		pixels.dispose();
		// enableDebug();
	}

	@Override
	public void dispose () {
		disableDebug();
		randomField.dispose();
		blur.dispose();
		shSsao.dispose();
		shMix.dispose();
		occlusionMap.dispose();
	}

	public void enableDebug () {
		GameEvents.gameRenderer.addListener(gameRendererEvent, GameRendererEvent.Type.BatchDebug, GameRendererEvent.Order.DEFAULT);
	}

	public void disableDebug () {
		GameEvents.gameRenderer.removeListener(gameRendererEvent, GameRendererEvent.Type.BatchDebug,
			GameRendererEvent.Order.DEFAULT);
	}

	public void setOcclusionThresholds (float no_occlusion, float full_occlusion) {
		shSsao.begin();
		shSsao.setUniformf("full_occlusion_treshold", full_occlusion);
		shSsao.setUniformf("no_occlusion_treshold", no_occlusion);
		shSsao.end();
	}

	public void setNormalDepthMap (Texture normalDepthMap) {
		this.normalDepthMap = normalDepthMap;
	}

	public void setRadius (float epsilon, float radius) {
		shSsao.begin();
		shSsao.setUniformf("radius", radius);
		shSsao.setUniformf("epsilon", epsilon);
		shSsao.end();
	}

	public void setPower (float power, float occlusion_power) {
		shSsao.begin();
		shSsao.setUniformf("occlusion_power", occlusion_power);
		shSsao.setUniformf("power", power);
		shSsao.end();
	}

	public void setPatternSize (int pattern_size) {
		shSsao.begin();
		shSsao.setUniformf("pattern_size", pattern_size);
		shSsao.end();
	}

	public void setSampleCount (int sample_count) {
		shSsao.begin();
		shSsao.setUniformi("sample_count", sample_count);
		shSsao.end();
	}

	private void dbgTexture (SpriteBatch batch, float scale, Texture tex, int index) {
		if (tex == null) return;

		float w = (tex.getWidth() * scale);
		float h = (tex.getHeight() * scale);
		float x = ScaleUtils.PlayWidth - w - Convert.scaledPixels(10);
		float y = index * Convert.scaledPixels(10);
		batch.draw(tex, x, y, w, h);
	}

	private void dbgTextureW (SpriteBatch batch, float width, Texture tex, int index) {
		if (tex == null) return;

		float ratio = ((float)ScaleUtils.PlayWidth / (float)ScaleUtils.PlayHeight);
		float h = width / ratio;
		float x = ScaleUtils.PlayWidth - width - Convert.scaledPixels(10);
		float y = index * Convert.scaledPixels(10);
		batch.draw(tex, x, y, width, h);
	}

	public void debug (SpriteBatch batch) {
		Gdx.gl.glDisable(GL20.GL_BLEND);
		Gdx.gl.glDisable(GL20.GL_CULL_FACE);
		Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
		batch.disableBlending();

		dbgTextureW(batch, 180, normalDepthMap, 12);
		dbgTextureW(batch, 180, occlusionMap.getResultTexture(), 24);
	}

	@Override
	public void render (final FrameBuffer src, final FrameBuffer dest) {
		Texture tsrc = src.getColorBufferTexture();

		Gdx.gl.glDisable(GL20.GL_CULL_FACE);
		Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

		Camera cam = GameEvents.gameRenderer.camPersp;

		mtxRot.set(cam.view);
		invPrj.set(cam.projection).inv();
		invRot.set(mtxRot).inv();

		occlusionMap.begin();
		occlusionMap.capture();
		{
			shSsao.begin();
			{
				Gdx.gl.glClearColor(0, 0, 0, 1);
				Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

				// samplers
				normalDepthMap.bind(0);
				randomField.bind(1);

				shSsao.setUniformi("normaldepth", 0);
				shSsao.setUniformi("random_field", 1);

				shSsao.setUniformMatrix("proj", cam.projection);
				shSsao.setUniformMatrix("inv_proj", invPrj);
				shSsao.setUniformMatrix("inv_rot", invRot);

				shSsao.setUniformf("viewport", occlusionMap.width, occlusionMap.height);
				shSsao.setUniformf("near", cam.near);
				shSsao.setUniformf("far", cam.far);

				quad.render(shSsao);
			}
			shSsao.end();

			// blur pass
			blur.setType(BlurType.Gaussian5x5b);
			blur.setPasses(2);
			blur.render(occlusionMap);
		}
		occlusionMap.end();

		if (dest != null) dest.begin();
		shMix.begin();
		{
			tsrc.bind(0);
			occlusionMap.getResultTexture().bind(1);

			shMix.setUniformi("scene", 0);
			shMix.setUniformi("occlusion_map", 1);

			quad.render(shMix);
		}
		shMix.end();
		if (dest != null) dest.end();
	}

	@Override
	public void rebind () {
	}
}
