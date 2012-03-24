package com.bitfire.uracer.postprocessing.filters;

import com.bitfire.uracer.postprocessing.IFilter;
import com.bitfire.uracer.utils.ShaderLoader;

public class Threshold extends Filter<Threshold> {

	public enum Param implements Parameter {
		// @formatter:off
		Texture( "u_texture0", 0 ),
		Threshold( "treshold", 0 ),
		ThresholdInvTx( "tresholdInvTx", 0 );
		// @formatter:on

		private String mnemonic;
		private int elementSize;

		private Param( String mnemonic, int elementSize ) {
			this.mnemonic = mnemonic;
			this.elementSize = elementSize;
		}

		@Override
		public String mnemonic() {
			return this.mnemonic;
		}

		@Override
		public int arrayElementSize() {
			return this.elementSize;
		}
	}

	public Threshold() {
		super( ShaderLoader.createShader( "screenspace", "threshold" ) );
		rebind();
	}

	private float gamma = 0;

	public void setTreshold( float gamma ) {
		this.gamma = gamma;
		setParams( Param.Threshold, gamma );
		setParams( Param.ThresholdInvTx, 1f / (1 - gamma) ).endParams();
	}

	@Override
	public void rebind() {
		setParams( Param.Texture, u_texture_1 );
		setTreshold( this.gamma );
	}

	@Override
	protected void compute() {
		inputTexture.bind( u_texture_1 );
		program.begin();
		IFilter.quad.render( program );
		program.end();
	}
}
