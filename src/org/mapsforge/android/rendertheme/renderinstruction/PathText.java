/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.android.rendertheme.renderinstruction;

import java.util.Locale;

import org.mapsforge.android.rendertheme.IRenderCallback;
import org.mapsforge.android.rendertheme.RenderThemeHandler;
import org.mapsforge.core.Tag;
import org.xml.sax.Attributes;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Typeface;

/**
 * Represents a text along a polyline on the map.
 */
public final class PathText implements RenderInstruction {
	/**
	 * @param elementName
	 *            the name of the XML element.
	 * @param attributes
	 *            the attributes of the XML element.
	 * @return a new PathText with the given rendering attributes.
	 */
	public static PathText create(String elementName, Attributes attributes) {
		String textKey = null;
		FontFamily fontFamily = FontFamily.DEFAULT;
		FontStyle fontStyle = FontStyle.NORMAL;
		float fontSize = 0;
		int fill = Color.BLACK;
		int stroke = Color.BLACK;
		float strokeWidth = 0;

		for (int i = 0; i < attributes.getLength(); ++i) {
			String name = attributes.getLocalName(i);
			String value = attributes.getValue(i);

			if ("k".equals(name)) {
				textKey = TextKey.getInstance(value);
			} else if ("font-family".equals(name)) {
				fontFamily = FontFamily.valueOf(value.toUpperCase(Locale.ENGLISH));
			} else if ("font-style".equals(name)) {
				fontStyle = FontStyle.valueOf(value.toUpperCase(Locale.ENGLISH));
			} else if ("font-size".equals(name)) {
				fontSize = Float.parseFloat(value);
			} else if ("fill".equals(name)) {
				fill = Color.parseColor(value);
			} else if ("stroke".equals(name)) {
				stroke = Color.parseColor(value);
			} else if ("stroke-width".equals(name)) {
				strokeWidth = Float.parseFloat(value);
			} else {
				RenderThemeHandler.logUnknownAttribute(elementName, name, value, i);
			}
		}

		validate(elementName, textKey, fontSize, strokeWidth);
		Typeface typeface = Typeface.create(fontFamily.toTypeface(), fontStyle.toInt());
		return new PathText(textKey, typeface, fontSize, fill, stroke, strokeWidth);
	}

	private static void validate(String elementName, String textKey, float fontSize, float strokeWidth) {
		if (textKey == null) {
			throw new IllegalArgumentException("missing attribute k for element: " + elementName);
		} else if (fontSize < 0) {
			throw new IllegalArgumentException("font-size must not be negative: " + fontSize);
		} else if (strokeWidth < 0) {
			throw new IllegalArgumentException("stroke-width must not be negative: " + strokeWidth);
		}
	}

	private final float mFontSize;
	private final Paint mPaint;
	private final Paint mStroke;
	private final String mTextKey;

	private PathText(String textKey, Typeface typeface, float fontSize, int fill, int stroke, float strokeWidth) {
		super();

		mTextKey = textKey;

		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPaint.setTextAlign(Align.CENTER);
		mPaint.setTypeface(typeface);
		mPaint.setColor(fill);

		mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
		mStroke.setStyle(Style.STROKE);
		mStroke.setTextAlign(Align.CENTER);
		mStroke.setTypeface(typeface);
		mStroke.setColor(stroke);
		mStroke.setStrokeWidth(strokeWidth);

		mFontSize = fontSize;
	}

	@Override
	public void destroy() {
		// do nothing
	}

	@Override
	public void renderNode(IRenderCallback renderCallback, Tag[] tags) {
		// do nothing
	}

	@Override
	public void renderWay(IRenderCallback renderCallback, Tag[] tags) {
		renderCallback.renderWayText(mTextKey, mPaint, mStroke);
	}

	@Override
	public void scaleStrokeWidth(float scaleFactor) {
		// do nothing
	}

	@Override
	public void scaleTextSize(float scaleFactor) {
		mPaint.setTextSize(mFontSize * scaleFactor);
		mStroke.setTextSize(mFontSize * scaleFactor);
	}
}
