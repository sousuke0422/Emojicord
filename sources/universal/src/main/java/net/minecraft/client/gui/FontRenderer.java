package net.minecraft.client.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.apache.commons.io.IOUtils;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FontRenderer implements IResourceManagerReloadListener {
	public static final ResourceLocation[] UNICODE_PAGE_LOCATIONS = new ResourceLocation[256];
	/** Array of width of all the characters in default.png */
	public final int[] charWidth = new int[256];
	/** the height in pixels of default text */
	public int FONT_HEIGHT = 9;
	public Random fontRandom = new Random();
	/** Array of the start/end column (in upper/lower nibble) for every glyph in the /font directory. */
	public final byte[] glyphWidth = new byte[65536];
	/**
	 * Array of RGB triplets defining the 16 standard chat colors followed by 16 darker version of the same colors for
	 * drop shadows.
	 */
	public final int[] colorCode = new int[32];
	public final ResourceLocation locationFontTexture;
	/** The RenderEngine used to load and setup glyph textures. */
	public final TextureManager renderEngine;
	/** Current X coordinate at which to draw the next character. */
	public float posX;
	/** Current Y coordinate at which to draw the next character. */
	public float posY;
	/** If true, strings should be rendered with Unicode fonts instead of the default.png font */
	public boolean unicodeFlag;
	/** If true, the Unicode Bidirectional Algorithm should be run before rendering any string. */
	public boolean bidiFlag;
	/** Used to specify new red value for the current color. */
	public float red;
	/** Used to specify new blue value for the current color. */
	public float blue;
	/** Used to specify new green value for the current color. */
	public float green;
	/** Used to speify new alpha value for the current color. */
	public float alpha;
	/** Text color of the currently rendering string. */
	public int textColor;
	/** Set if the "k" style (random) is active in currently rendering string */
	public boolean randomStyle;
	/** Set if the "l" style (bold) is active in currently rendering string */
	public boolean boldStyle;
	/** Set if the "o" style (italic) is active in currently rendering string */
	public boolean italicStyle;
	/** Set if the "n" style (underlined) is active in currently rendering string */
	public boolean underlineStyle;
	/** Set if the "m" style (strikethrough) is active in currently rendering string */
	public boolean strikethroughStyle;

	public FontRenderer(final GameSettings gameSettingsIn, final ResourceLocation location, final TextureManager textureManagerIn, final boolean unicode) {
		this.locationFontTexture = location;
		this.renderEngine = textureManagerIn;
		this.unicodeFlag = unicode;
		bindTexture(this.locationFontTexture);

		for (int i = 0; i<32; ++i) {
			final int j = (i>>3&1)*85;
			int k = (i>>2&1)*170+j;
			int l = (i>>1&1)*170+j;
			int i1 = (i>>0&1)*170+j;

			if (i==6)
				k += 85;

			if (gameSettingsIn.anaglyph) {
				final int j1 = (k*30+l*59+i1*11)/100;
				final int k1 = (k*30+l*70)/100;
				final int l1 = (k*30+i1*70)/100;
				k = j1;
				l = k1;
				i1 = l1;
			}

			if (i>=16) {
				k /= 4;
				l /= 4;
				i1 /= 4;
			}

			this.colorCode[i] = (k&255)<<16|(l&255)<<8|i1&255;
		}

		readGlyphSizes();
	}

	@Override
	public void onResourceManagerReload(final IResourceManager resourceManager) {
		readFontTexture();
		readGlyphSizes();
	}

	private void readFontTexture() {
		IResource iresource = null;
		BufferedImage bufferedimage;

		try {
			iresource = getResource(this.locationFontTexture);
			bufferedimage = TextureUtil.readBufferedImage(iresource.getInputStream());
		} catch (final IOException ioexception) {
			throw new RuntimeException(ioexception);
		} finally {
			IOUtils.closeQuietly(iresource);
		}

		final int lvt_3_2_ = bufferedimage.getWidth();
		final int lvt_4_1_ = bufferedimage.getHeight();
		final int[] lvt_5_1_ = new int[lvt_3_2_*lvt_4_1_];
		bufferedimage.getRGB(0, 0, lvt_3_2_, lvt_4_1_, lvt_5_1_, 0, lvt_3_2_);
		final int lvt_6_1_ = lvt_4_1_/16;
		final int lvt_7_1_ = lvt_3_2_/16;
		final boolean lvt_8_1_ = true;
		final float lvt_9_1_ = 8.0F/lvt_7_1_;

		for (int lvt_10_1_ = 0; lvt_10_1_<256; ++lvt_10_1_) {
			final int j1 = lvt_10_1_%16;
			final int k1 = lvt_10_1_/16;

			if (lvt_10_1_==32)
				this.charWidth[lvt_10_1_] = 4;

			int l1;

			for (l1 = lvt_7_1_-1; l1>=0; --l1) {
				final int i2 = j1*lvt_7_1_+l1;
				boolean flag1 = true;

				for (int j2 = 0; j2<lvt_6_1_&&flag1; ++j2) {
					final int k2 = (k1*lvt_7_1_+j2)*lvt_3_2_;

					if ((lvt_5_1_[i2+k2]>>24&255)!=0)
						flag1 = false;
				}

				if (!flag1)
					break;
			}

			++l1;
			this.charWidth[lvt_10_1_] = (int) (0.5D+l1*lvt_9_1_)+1;
		}
	}

	private void readGlyphSizes() {
		IResource iresource = null;

		try {
			iresource = getResource(new ResourceLocation("font/glyph_sizes.bin"));
			iresource.getInputStream().read(this.glyphWidth);
		} catch (final IOException ioexception) {
			throw new RuntimeException(ioexception);
		} finally {
			IOUtils.closeQuietly(iresource);
		}
	}

	/**
	 * Render the given char
	 */
	private float renderChar(final char ch, final boolean italic) {
		if (ch==160)
			return 4.0F; // forge: display nbsp as space. MC-2595
		if (ch==' ')
			return 4.0F;
		else {
			final int i = "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000"
					.indexOf(ch);
			return i!=-1&&!this.unicodeFlag ? renderDefaultChar(i, italic) : renderUnicodeChar(ch, italic);
		}
	}

	/**
	 * Render a single character with the default.png font at current (posX,posY) location...
	 */
	protected float renderDefaultChar(final int ch, final boolean italic) {
		final int i = ch%16*8;
		final int j = ch/16*8;
		final int k = italic ? 1 : 0;
		bindTexture(this.locationFontTexture);
		final int l = this.charWidth[ch];
		final float f = l-0.01F;
		GlStateManager.glBegin(5);
		GlStateManager.glTexCoord2f(i/128.0F, j/128.0F);
		GlStateManager.glVertex3f(this.posX+k, this.posY, 0.0F);
		GlStateManager.glTexCoord2f(i/128.0F, (j+7.99F)/128.0F);
		GlStateManager.glVertex3f(this.posX-k, this.posY+7.99F, 0.0F);
		GlStateManager.glTexCoord2f((i+f-1.0F)/128.0F, j/128.0F);
		GlStateManager.glVertex3f(this.posX+f-1.0F+k, this.posY, 0.0F);
		GlStateManager.glTexCoord2f((i+f-1.0F)/128.0F, (j+7.99F)/128.0F);
		GlStateManager.glVertex3f(this.posX+f-1.0F-k, this.posY+7.99F, 0.0F);
		GlStateManager.glEnd();
		return l;
	}

	private ResourceLocation getUnicodePageLocation(final int page) {
		if (UNICODE_PAGE_LOCATIONS[page]==null)
			UNICODE_PAGE_LOCATIONS[page] = new ResourceLocation(String.format("textures/font/unicode_page_%02x.png", page));

		return UNICODE_PAGE_LOCATIONS[page];
	}

	/**
	 * Load one of the /font/glyph_XX.png into a new GL texture and store the texture ID in glyphTextureName array.
	 */
	private void loadGlyphTexture(final int page) {
		bindTexture(getUnicodePageLocation(page));
	}

	/**
	 * Render a single Unicode character at current (posX,posY) location using one of the /font/glyph_XX.png files...
	 */
	protected float renderUnicodeChar(final char ch, final boolean italic) {
		final int i = this.glyphWidth[ch]&255;

		if (i==0)
			return 0.0F;
		else {
			final int j = ch/256;
			loadGlyphTexture(j);
			final int k = i>>>4;
			final int l = i&15;
			final float f = k;
			final float f1 = l+1;
			final float f2 = ch%16*16+f;
			final float f3 = (ch&255)/16*16;
			final float f4 = f1-f-0.02F;
			final float f5 = italic ? 1.0F : 0.0F;
			GlStateManager.glBegin(5);
			GlStateManager.glTexCoord2f(f2/256.0F, f3/256.0F);
			GlStateManager.glVertex3f(this.posX+f5, this.posY, 0.0F);
			GlStateManager.glTexCoord2f(f2/256.0F, (f3+15.98F)/256.0F);
			GlStateManager.glVertex3f(this.posX-f5, this.posY+7.99F, 0.0F);
			GlStateManager.glTexCoord2f((f2+f4)/256.0F, f3/256.0F);
			GlStateManager.glVertex3f(this.posX+f4/2.0F+f5, this.posY, 0.0F);
			GlStateManager.glTexCoord2f((f2+f4)/256.0F, (f3+15.98F)/256.0F);
			GlStateManager.glVertex3f(this.posX+f4/2.0F-f5, this.posY+7.99F, 0.0F);
			GlStateManager.glEnd();
			return (f1-f)/2.0F+1.0F;
		}
	}

	/**
	 * Draws the specified string with a shadow.
	 */
	public int drawStringWithShadow(final String text, final float x, final float y, final int color) {
		return this.drawString(text, x, y, color, true);
	}

	/**
	 * Draws the specified string.
	 */
	public int drawString(final String text, final int x, final int y, final int color) {
		return this.drawString(text, x, y, color, false);
	}

	/**
	 * Draws the specified string.
	 */
	public int drawString(final String text, final float x, final float y, final int color, final boolean dropShadow) {
		enableAlpha();
		resetStyles();
		int i;

		if (dropShadow) {
			i = renderString(text, x+1.0F, y+1.0F, color, true);
			i = Math.max(i, renderString(text, x, y, color, false));
		} else
			i = renderString(text, x, y, color, false);

		return i;
	}

	/**
	 * Apply Unicode Bidirectional Algorithm to string and return a new possibly reordered string for visual rendering.
	 */
	private String bidiReorder(final String text) {
		try {
			final Bidi bidi = new Bidi(new ArabicShaping(8).shape(text), 127);
			bidi.setReorderingMode(0);
			return bidi.writeReordered(2);
		} catch (final ArabicShapingException var3) {
			return text;
		}
	}

	/**
	 * Reset all style flag fields in the class to false; called at the start of string rendering
	 */
	private void resetStyles() {
		this.randomStyle = false;
		this.boldStyle = false;
		this.italicStyle = false;
		this.underlineStyle = false;
		this.strikethroughStyle = false;
	}

	/**
	 * Render a single line string at the current (posX,posY) and update posX
	 */
	private void renderStringAtPos(final String text, final boolean shadow) {
		for (int i = 0; i<text.length(); ++i) {
			char c0 = text.charAt(i);

			if (c0==167&&i+1<text.length()) {
				int i1 = "0123456789abcdefklmnor".indexOf(String.valueOf(text.charAt(i+1)).toLowerCase(Locale.ROOT).charAt(0));

				if (i1<16) {
					this.randomStyle = false;
					this.boldStyle = false;
					this.strikethroughStyle = false;
					this.underlineStyle = false;
					this.italicStyle = false;

					if (i1<0||i1>15)
						i1 = 15;

					if (shadow)
						i1 += 16;

					final int j1 = this.colorCode[i1];
					this.textColor = j1;
					setColor((j1>>16)/255.0F, (j1>>8&255)/255.0F, (j1&255)/255.0F, this.alpha);
				} else if (i1==16)
					this.randomStyle = true;
				else if (i1==17)
					this.boldStyle = true;
				else if (i1==18)
					this.strikethroughStyle = true;
				else if (i1==19)
					this.underlineStyle = true;
				else if (i1==20)
					this.italicStyle = true;
				else if (i1==21) {
					this.randomStyle = false;
					this.boldStyle = false;
					this.strikethroughStyle = false;
					this.underlineStyle = false;
					this.italicStyle = false;
					setColor(this.red, this.blue, this.green, this.alpha);
				}

				++i;
			} else {
				int j = "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000"
						.indexOf(c0);

				if (this.randomStyle&&j!=-1) {
					final int k = getCharWidth(c0);
					char c1;

					while (true) {
						j = this.fontRandom.nextInt(
								"\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000"
										.length());
						c1 = "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000"
								.charAt(j);

						if (k==getCharWidth(c1))
							break;
					}

					c0 = c1;
				}

				final float f1 = j==-1||this.unicodeFlag ? 0.5f : 1f;
				final boolean flag = (c0==0||j==-1||this.unicodeFlag)&&shadow;

				if (flag) {
					this.posX -= f1;
					this.posY -= f1;
				}

				float f = renderChar(c0, this.italicStyle);

				if (flag) {
					this.posX += f1;
					this.posY += f1;
				}

				if (this.boldStyle) {
					this.posX += f1;

					if (flag) {
						this.posX -= f1;
						this.posY -= f1;
					}

					renderChar(c0, this.italicStyle);
					this.posX -= f1;

					if (flag) {
						this.posX += f1;
						this.posY += f1;
					}

					++f;
				}
				doDraw(f);
			}
		}
	}

	protected void doDraw(final float f) {
		{
			{

				if (this.strikethroughStyle) {
					final Tessellator tessellator = Tessellator.getInstance();
					final BufferBuilder bufferbuilder = tessellator.getBuffer();
					GlStateManager.disableTexture2D();
					bufferbuilder.begin(7, DefaultVertexFormats.POSITION);
					bufferbuilder.pos(this.posX, this.posY+this.FONT_HEIGHT/2, 0.0D).endVertex();
					bufferbuilder.pos(this.posX+f, this.posY+this.FONT_HEIGHT/2, 0.0D).endVertex();
					bufferbuilder.pos(this.posX+f, this.posY+this.FONT_HEIGHT/2-1.0F, 0.0D).endVertex();
					bufferbuilder.pos(this.posX, this.posY+this.FONT_HEIGHT/2-1.0F, 0.0D).endVertex();
					tessellator.draw();
					GlStateManager.enableTexture2D();
				}

				if (this.underlineStyle) {
					final Tessellator tessellator1 = Tessellator.getInstance();
					final BufferBuilder bufferbuilder1 = tessellator1.getBuffer();
					GlStateManager.disableTexture2D();
					bufferbuilder1.begin(7, DefaultVertexFormats.POSITION);
					final int l = this.underlineStyle ? -1 : 0;
					bufferbuilder1.pos(this.posX+l, this.posY+this.FONT_HEIGHT, 0.0D).endVertex();
					bufferbuilder1.pos(this.posX+f, this.posY+this.FONT_HEIGHT, 0.0D).endVertex();
					bufferbuilder1.pos(this.posX+f, this.posY+this.FONT_HEIGHT-1.0F, 0.0D).endVertex();
					bufferbuilder1.pos(this.posX+l, this.posY+this.FONT_HEIGHT-1.0F, 0.0D).endVertex();
					tessellator1.draw();
					GlStateManager.enableTexture2D();
				}

				this.posX += (int) f;
			}
		}
	}

	/**
	 * Render string either left or right aligned depending on bidiFlag
	 */
	private int renderStringAligned(final String text, int x, final int y, final int width, final int color, final boolean dropShadow) {
		if (this.bidiFlag) {
			final int i = getStringWidth(bidiReorder(text));
			x = x+width-i;
		}

		return renderString(text, x, y, color, dropShadow);
	}

	/**
	 * Render single line string by setting GL color, current (posX,posY), and calling renderStringAtPos()
	 */
	private int renderString(String text, final float x, final float y, int color, final boolean dropShadow) {
		if (text==null)
			return 0;
		else {
			if (this.bidiFlag)
				text = bidiReorder(text);

			if ((color&-67108864)==0)
				color |= -16777216;

			if (dropShadow)
				color = (color&16579836)>>2|color&-16777216;

			this.red = (color>>16&255)/255.0F;
			this.blue = (color>>8&255)/255.0F;
			this.green = (color&255)/255.0F;
			this.alpha = (color>>24&255)/255.0F;
			setColor(this.red, this.blue, this.green, this.alpha);
			this.posX = x;
			this.posY = y;
			renderStringAtPos(text, dropShadow);
			return (int) this.posX;
		}
	}

	/**
	 * Returns the width of this string. Equivalent of FontMetrics.stringWidth(String s).
	 */
	public int getStringWidth(final String text) {
		if (text==null)
			return 0;
		else {
			int i = 0;
			boolean flag = false;

			for (int j = 0; j<text.length(); ++j) {
				char c0 = text.charAt(j);
				int k = getCharWidth(c0);

				if (k<0&&j<text.length()-1) {
					++j;
					c0 = text.charAt(j);

					if (c0!='l'&&c0!='L') {
						if (c0=='r'||c0=='R')
							flag = false;
					} else
						flag = true;

					k = 0;
				}

				i += k;

				if (flag&&k>0)
					++i;
			}

			return i;
		}
	}

	/**
	 * Returns the width of this character as rendered.
	 */
	public int getCharWidth(final char character) {
		if (character==160)
			return 4; // forge: display nbsp as space. MC-2595
		if (character==167)
			return -1;
		else if (character==' ')
			return 4;
		else {
			final int i = "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000"
					.indexOf(character);

			if (character>0&&i!=-1&&!this.unicodeFlag)
				return this.charWidth[i];
			else if (this.glyphWidth[character]!=0) {
				final int j = this.glyphWidth[character]&255;
				final int k = j>>>4;
				int l = j&15;
				++l;
				return (l-k)/2+1;
			} else
				return 0;
		}
	}

	/**
	 * Trims a string to fit a specified Width.
	 */
	public String trimStringToWidth(final String text, final int width) {
		return this.trimStringToWidth(text, width, false);
	}

	/**
	 * Trims a string to a specified width, optionally starting from the end and working backwards.
	 * <h3>Samples:</h3>
	 * (Assuming that {@link #getCharWidth(char)} returns <code>6</code> for all of the characters in
	 * <code>0123456789</code> on the current resource pack)
	 * <table>
	 * <tr><th>Input</th><th>Returns</th></tr>
	 * <tr><td><code>trimStringToWidth("0123456789", 1, false)</code></td><td><samp>""</samp></td></tr>
	 * <tr><td><code>trimStringToWidth("0123456789", 6, false)</code></td><td><samp>"0"</samp></td></tr>
	 * <tr><td><code>trimStringToWidth("0123456789", 29, false)</code></td><td><samp>"0123"</samp></td></tr>
	 * <tr><td><code>trimStringToWidth("0123456789", 30, false)</code></td><td><samp>"01234"</samp></td></tr>
	 * <tr><td><code>trimStringToWidth("0123456789", 9001, false)</code></td><td><samp>"0123456789"</samp></td></tr>
	 * <tr><td><code>trimStringToWidth("0123456789", 1, true)</code></td><td><samp>""</samp></td></tr>
	 * <tr><td><code>trimStringToWidth("0123456789", 6, true)</code></td><td><samp>"9"</samp></td></tr>
	 * <tr><td><code>trimStringToWidth("0123456789", 29, true)</code></td><td><samp>"6789"</samp></td></tr>
	 * <tr><td><code>trimStringToWidth("0123456789", 30, true)</code></td><td><samp>"56789"</samp></td></tr>
	 * <tr><td><code>trimStringToWidth("0123456789", 9001, true)</code></td><td><samp>"0123456789"</samp></td></tr>
	 * </table>
	 */
	public String trimStringToWidth(final String text, final int width, final boolean reverse) {
		final StringBuilder stringbuilder = new StringBuilder();
		int i = 0;
		final int j = reverse ? text.length()-1 : 0;
		final int k = reverse ? -1 : 1;
		boolean flag = false;
		boolean flag1 = false;

		for (int l = j; l>=0&&l<text.length()&&i<width; l += k) {
			final char c0 = text.charAt(l);
			final int i1 = getCharWidth(c0);

			if (flag) {
				flag = false;

				if (c0!='l'&&c0!='L') {
					if (c0=='r'||c0=='R')
						flag1 = false;
				} else
					flag1 = true;
			} else if (i1<0)
				flag = true;
			else {
				i += i1;

				if (flag1)
					++i;
			}

			if (i>width)
				break;

			if (reverse)
				stringbuilder.insert(0, c0);
			else
				stringbuilder.append(c0);
		}

		return stringbuilder.toString();
	}

	/**
	 * Remove all newline characters from the end of the string
	 */
	private String trimStringNewline(String text) {
		while (text!=null&&text.endsWith("\n"))
			text = text.substring(0, text.length()-1);

		return text;
	}

	/**
	 * Splits and draws a String with wordwrap (maximum length is parameter k)
	 */
	public void drawSplitString(String str, final int x, final int y, final int wrapWidth, final int textColor) {
		resetStyles();
		this.textColor = textColor;
		str = trimStringNewline(str);
		renderSplitString(str, x, y, wrapWidth, false);
	}

	/**
	 * Perform actual work of rendering a multi-line string with wordwrap and with darker drop shadow color if flag is
	 * set
	 */
	private void renderSplitString(final String str, final int x, int y, final int wrapWidth, final boolean addShadow) {
		for (final String s : listFormattedStringToWidth(str, wrapWidth)) {
			renderStringAligned(s, x, y, wrapWidth, this.textColor, addShadow);
			y += this.FONT_HEIGHT;
		}
	}

	/**
	 * Returns the height (in pixels) of the given string if it is wordwrapped to the given max width.
	 */
	public int getWordWrappedHeight(final String str, final int maxLength) {
		return this.FONT_HEIGHT*listFormattedStringToWidth(str, maxLength).size();
	}

	/**
	 * Set unicodeFlag controlling whether strings should be rendered with Unicode fonts instead of the default.png
	 * font.
	 */
	public void setUnicodeFlag(final boolean unicodeFlagIn) {
		this.unicodeFlag = unicodeFlagIn;
	}

	/**
	 * Get unicodeFlag controlling whether strings should be rendered with Unicode fonts instead of the default.png
	 * font.
	 */
	public boolean getUnicodeFlag() {
		return this.unicodeFlag;
	}

	/**
	 * Set bidiFlag to control if the Unicode Bidirectional Algorithm should be run before rendering any string.
	 */
	public void setBidiFlag(final boolean bidiFlagIn) {
		this.bidiFlag = bidiFlagIn;
	}

	/**
	 * Breaks a string into a list of pieces where the width of each line is always less than or equal to the provided
	 * width. Formatting codes will be preserved between lines.
	 */
	public List<String> listFormattedStringToWidth(final String str, final int wrapWidth) {
		return Arrays.<String> asList(wrapFormattedStringToWidth(str, wrapWidth).split("\n"));
	}

	/**
	 * Inserts newline and formatting into a string to wrap it within the specified width.
	 */
	String wrapFormattedStringToWidth(final String str, final int wrapWidth) {
		final int i = sizeStringToWidth(str, wrapWidth);

		if (str.length()<=i)
			return str;
		else {
			final String s = str.substring(0, i);
			final char c0 = str.charAt(i);
			final boolean flag = c0==' '||c0=='\n';
			final String s1 = getFormatFromString(s)+str.substring(i+(flag ? 1 : 0));
			return s+"\n"+wrapFormattedStringToWidth(s1, wrapWidth);
		}
	}

	/**
	 * Determines how many characters from the string will fit into the specified width.
	 */
	private int sizeStringToWidth(final String str, final int wrapWidth) {
		final int i = str.length();
		int j = 0;
		int k = 0;
		int l = -1;

		for (boolean flag = false; k<i; ++k) {
			final char c0 = str.charAt(k);

			switch (c0) {
				case '\n':
					--k;
					break;
				case ' ':
					l = k;
				default:
					j += getCharWidth(c0);

					if (flag)
						++j;

					break;
				case '\u00a7':

					if (k<i-1) {
						++k;
						final char c1 = str.charAt(k);

						if (c1!='l'&&c1!='L') {
							if (c1=='r'||c1=='R'||isFormatColor(c1))
								flag = false;
						} else
							flag = true;
					}
			}

			if (c0=='\n') {
				++k;
				l = k;
				break;
			}

			if (j>wrapWidth)
				break;
		}

		return k!=i&&l!=-1&&l<k ? l : k;
	}

	/**
	 * Checks if the char code is a hexadecimal character, used to set colour.
	 */
	private static boolean isFormatColor(final char colorChar) {
		return colorChar>='0'&&colorChar<='9'||colorChar>='a'&&colorChar<='f'||colorChar>='A'&&colorChar<='F';
	}

	/**
	 * Checks if the char code is O-K...lLrRk-o... used to set special formatting.
	 */
	private static boolean isFormatSpecial(final char formatChar) {
		return formatChar>='k'&&formatChar<='o'||formatChar>='K'&&formatChar<='O'||formatChar=='r'||formatChar=='R';
	}

	/**
	 * Digests a string for nonprinting formatting characters then returns a string containing only that formatting.
	 */
	public static String getFormatFromString(final String text) {
		String s = "";
		int i = -1;
		final int j = text.length();

		while ((i = text.indexOf(167, i+1))!=-1)
			if (i<j-1) {
				final char c0 = text.charAt(i+1);

				if (isFormatColor(c0))
					s = "\u00a7"+c0;
				else if (isFormatSpecial(c0))
					s = s+"\u00a7"+c0;
			}

		return s;
	}

	/**
	 * Get bidiFlag that controls if the Unicode Bidirectional Algorithm should be run before rendering any string
	 */
	public boolean getBidiFlag() {
		return this.bidiFlag;
	}

	protected void setColor(final float r, final float g, final float b, final float a) {
		GlStateManager.color(r, g, b, a);
	}

	protected void enableAlpha() {
		GlStateManager.enableAlpha();
	}

	protected void bindTexture(final ResourceLocation location) {
		this.renderEngine.bindTexture(location);
	}

	protected IResource getResource(final ResourceLocation location) throws IOException {
		return Minecraft.getMinecraft().getResourceManager().getResource(location);
	}

	public int getColorCode(final char character) {
		final int i = "0123456789abcdef".indexOf(character);
		return i>=0&&i<this.colorCode.length ? this.colorCode[i] : -1;
	}
}