package com.simibubi.create.foundation.utility;

import java.nio.ByteBuffer;

import javax.vecmath.Matrix4f;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.Direction.Axis;

public class SuperByteBuffer {

	public interface IVertexLighter {
		public int getPackedLight(float x, float y, float z);
	}

	public static final int FORMAT_LENGTH = DefaultVertexFormats.BLOCK.getSize();
	protected ByteBuffer original;
	protected ByteBuffer mutable;

	// Vertex Position
	private Matrix4f transforms;
	private Matrix4f t;

	// Vertex Texture Coords
	private boolean shouldShiftUV;
	private float uShift, vShift;

	// Vertex Lighting
	private boolean shouldLight;
	private IVertexLighter vertexLighter;
	private int packedLightCoords;

	// Vertex Coloring
	private boolean shouldColor;
	private int r, g, b, a;

	public SuperByteBuffer(ByteBuffer original) {
		original.rewind();
		this.original = original;

		this.mutable = GLAllocation.createDirectByteBuffer(original.capacity());
		this.mutable.order(original.order());
		this.mutable.limit(original.limit());
		mutable.put(this.original);
		mutable.rewind();

		t = new Matrix4f();
		transforms = new Matrix4f();
		transforms.setIdentity();
	}

	public ByteBuffer build() {
		original.rewind();
		mutable.rewind();
		float x, y, z = 0;
		float x2, y2, z2 = 0;

		Matrix4f t = transforms;
		for (int vertex = 0; vertex < vertexCount(original); vertex++) {
			x = getX(original, vertex);
			y = getY(original, vertex);
			z = getZ(original, vertex);

			x2 = t.m00 * x + t.m01 * y + t.m02 * z + t.m03;
			y2 = t.m10 * x + t.m11 * y + t.m12 * z + t.m13;
			z2 = t.m20 * x + t.m21 * y + t.m22 * z + t.m23;

			putPos(mutable, vertex, x2, y2, z2);

			if (shouldColor) {
				byte lumByte = getR(original, vertex);
				float lum = (lumByte < 0 ? 255 + lumByte : lumByte) / 256f;
				int r2 = (int) (r * lum);
				int g2 = (int) (g * lum);
				int b2 = (int) (b * lum);
				putColor(mutable, vertex, (byte) r2, (byte) g2, (byte) b2, (byte) a);
			}

			if (shouldShiftUV)
				putUV(mutable, vertex, getU(original, vertex) + uShift, getV(original, vertex) + vShift);

			if (shouldLight) {
				if (vertexLighter != null)
					putLight(mutable, vertex, vertexLighter.getPackedLight(x2, y2, z2));
				else
					putLight(mutable, vertex, packedLightCoords);
			}
		}

		t.setIdentity();
		shouldShiftUV = false;
		shouldColor = false;
		shouldLight = false;
		return mutable;
	}

	public void renderInto(BufferBuilder buffer) {
		buffer.putBulkData(build());
	}

	public SuperByteBuffer translate(double x, double y, double z) {
		return translate((float) x, (float) y, (float) z);
	}

	public SuperByteBuffer translate(float x, float y, float z) {
		transforms.m03 += x;
		transforms.m13 += y;
		transforms.m23 += z;
		return this;
	}

	public SuperByteBuffer rotate(Axis axis, float angle) {
		if (angle == 0)
			return this;
		t.setIdentity();
		if (axis == Axis.X)
			t.rotX(angle);
		else if (axis == Axis.Y)
			t.rotY(angle);
		else
			t.rotZ(angle);
		transforms.mul(t, transforms);
		return this;
	}

	public SuperByteBuffer rotateCentered(Axis axis, float angle) {
		return translate(-.5f, -.5f, -.5f).rotate(axis, angle).translate(.5f, .5f, .5f);
	}

	public SuperByteBuffer shiftUV(TextureAtlasSprite from, TextureAtlasSprite to) {
		shouldShiftUV = true;
		uShift = to.getMinU() - from.getMinU();
		vShift = to.getMinV() - from.getMinV();
		return this;
	}

	public SuperByteBuffer shiftUVtoSheet(TextureAtlasSprite from, TextureAtlasSprite to, int sheetX, int sheetY) {
		shouldShiftUV = true;
		uShift = to.getInterpolatedU(sheetX * 16f / to.getWidth()) - from.getMinU();
		vShift = to.getInterpolatedV(sheetY * 16f / to.getHeight()) - from.getMinV();
		return this;
	}

	public SuperByteBuffer dontShiftUV() {
		shouldShiftUV = false;
		uShift = 0;
		vShift = 0;
		return this;
	}

	public SuperByteBuffer light(int packedLightCoords) {
		shouldLight = true;
		this.packedLightCoords = packedLightCoords;
		return this;
	}

	public SuperByteBuffer light(IVertexLighter lighter) {
		shouldLight = true;
		vertexLighter = lighter;
		return this;
	}

	public SuperByteBuffer color(int color) {
		shouldColor = true;
		r = ((color >> 16) & 0xFF);
		g = ((color >> 8) & 0xFF);
		b = (color & 0xFF);
		a = 255;
		return this;
	}

	protected int vertexCount(ByteBuffer buffer) {
		return buffer.limit() / FORMAT_LENGTH;
	}

	protected int getBufferPosition(int vertexIndex) {
		return vertexIndex * FORMAT_LENGTH;
	}

	protected float getX(ByteBuffer buffer, int index) {
		return buffer.getFloat(getBufferPosition(index));
	}

	protected float getY(ByteBuffer buffer, int index) {
		return buffer.getFloat(getBufferPosition(index) + 4);
	}

	protected float getZ(ByteBuffer buffer, int index) {
		return buffer.getFloat(getBufferPosition(index) + 8);
	}

	protected byte getR(ByteBuffer buffer, int index) {
		return buffer.get(getBufferPosition(index) + 12);
	}

	protected byte getG(ByteBuffer buffer, int index) {
		return buffer.get(getBufferPosition(index) + 13);
	}

	protected byte getB(ByteBuffer buffer, int index) {
		return buffer.get(getBufferPosition(index) + 14);
	}

	protected byte getA(ByteBuffer buffer, int index) {
		return buffer.get(getBufferPosition(index) + 15);
	}

	protected float getU(ByteBuffer buffer, int index) {
		return buffer.getFloat(getBufferPosition(index) + 16);
	}

	protected float getV(ByteBuffer buffer, int index) {
		return buffer.getFloat(getBufferPosition(index) + 20);
	}

	protected void putPos(ByteBuffer buffer, int index, float x, float y, float z) {
		int pos = getBufferPosition(index);
		buffer.putFloat(pos, x);
		buffer.putFloat(pos + 4, y);
		buffer.putFloat(pos + 8, z);
	}

	protected void putUV(ByteBuffer buffer, int index, float u, float v) {
		int pos = getBufferPosition(index);
		buffer.putFloat(pos + 16, u);
		buffer.putFloat(pos + 20, v);
	}

	protected void putLight(ByteBuffer buffer, int index, int packedLight) {
		buffer.putInt(getBufferPosition(index) + 24, packedLight);
	}

	protected void putColor(ByteBuffer buffer, int index, byte r, byte g, byte b, byte a) {
		int bufferPosition = getBufferPosition(index);
		buffer.put(bufferPosition + 12, r);
		buffer.put(bufferPosition + 13, g);
		buffer.put(bufferPosition + 14, b);
		buffer.put(bufferPosition + 15, a);
	}

}
