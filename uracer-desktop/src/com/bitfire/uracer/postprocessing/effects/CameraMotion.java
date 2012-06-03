package com.bitfire.uracer.postprocessing.effects;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.bitfire.uracer.postprocessing.PostProcessorEffect;
import com.bitfire.uracer.postprocessing.filters.CameraBlur;

public class CameraMotion extends PostProcessorEffect {
	private CameraBlur camblur;

	public CameraMotion( Texture depthMap ) {
		camblur = new CameraBlur();
		camblur.setDepthMap( depthMap );
	}

	@Override
	public void dispose() {
		camblur.dispose();
	}

	public void setMatrices( Matrix4 invViewProj, Matrix4 prevViewProj ) {
		camblur.setMatrices( invViewProj, prevViewProj );
	}

	@Override
	public void rebind() {
		camblur.rebind();
	}

	@Override
	public void render( FrameBuffer src, FrameBuffer dest ) {
		camblur.setInput( src ).setOutput( dest ).render();
	};
}
