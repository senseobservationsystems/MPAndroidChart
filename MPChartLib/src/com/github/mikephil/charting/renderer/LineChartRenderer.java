
package com.github.mikephil.charting.renderer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.buffer.CircleBuffer;
import com.github.mikephil.charting.buffer.LineBuffer;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.LineDataProvider;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.List;

public class LineChartRenderer extends LineScatterCandleRadarRenderer {

    protected LineDataProvider mChart;

    /**
     * paint for the inner circle of the value indicators
     */
    protected Paint mCirclePaintInner;

    /**
     * Bitmap object used for drawing the paths (otherwise they are too long if
     * rendered directly on the canvas)
     */
    protected Bitmap mDrawBitmap;

    /**
     * on this canvas, the paths are rendered, it is initialized with the
     * pathBitmap
     */
    protected Canvas mBitmapCanvas;

    protected Path cubicPath = new Path();
    protected Path cubicFillPath = new Path();

    protected LineBuffer[] mLineBuffers;

    protected CircleBuffer[] mCircleBuffers;

    public LineChartRenderer(LineDataProvider chart, ChartAnimator animator,
                             ViewPortHandler viewPortHandler) {
        super(animator, viewPortHandler);
        mChart = chart;

        mCirclePaintInner = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaintInner.setStyle(Paint.Style.FILL);
        mCirclePaintInner.setColor(Color.WHITE);
    }

    @Override
    public void initBuffers() {

        LineData lineData = mChart.getLineData();
        mLineBuffers = new LineBuffer[lineData.getDataSetCount()];
        mCircleBuffers = new CircleBuffer[lineData.getDataSetCount()];

        for (int i = 0; i < mLineBuffers.length; i++) {
            LineDataSet set = lineData.getDataSetByIndex(i);
            mLineBuffers[i] = new LineBuffer(set.getEntryCount() * 4 - 4);
            mCircleBuffers[i] = new CircleBuffer(set.getEntryCount() * 2);
        }
    }

    @Override
    public void drawData(Canvas c) {

        int width = (int) mViewPortHandler.getChartWidth();
        int height = (int) mViewPortHandler.getChartHeight();

        if (mDrawBitmap == null
                || (mDrawBitmap.getWidth() != width)
                || (mDrawBitmap.getHeight() != height)) {

            if (width > 0 && height > 0) {

                mDrawBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);
                mBitmapCanvas = new Canvas(mDrawBitmap);
            } else
                return;
        }

        mDrawBitmap.eraseColor(Color.TRANSPARENT);

        LineData lineData = mChart.getLineData();

        for (LineDataSet set : lineData.getDataSets()) {

            if (set.isVisible() && set.getEntryCount() > 0)
                drawDataSet(c, set);
        }

        c.drawBitmap(mDrawBitmap, 0, 0, mRenderPaint);
    }

    protected void drawDataSet(Canvas c, LineDataSet dataSet) {

        List<Entry> entries = dataSet.getYVals();

        if (entries.size() < 1)
            return;

        mRenderPaint.setStrokeWidth(dataSet.getLineWidth());
        mRenderPaint.setPathEffect(dataSet.getDashPathEffect());

        // if drawing cubic lines is enabled
        if (dataSet.isDrawCubicEnabled()) {

            drawCubic(c, dataSet, entries);

            // draw normal (straight) lines
        } else {
            drawLinear(c, dataSet, entries);
        }

        mRenderPaint.setPathEffect(null);
    }

    /**
     * Draws a cubic line.
     *
     * @param c
     * @param dataSet
     * @param entries
     */
    protected void drawCubic(Canvas c, LineDataSet dataSet, List<Entry> entries) {

        Transformer trans = mChart.getTransformer(dataSet.getAxisDependency());

        Entry entryFrom = dataSet.getEntryForXIndex(mMinX);
        Entry entryTo = dataSet.getEntryForXIndex(mMaxX);

        int diff = (entryFrom == entryTo) ? 1 : 0;
        int minx = Math.max(dataSet.getEntryPosition(entryFrom) - diff, 0);
        int maxx = Math.min(Math.max(
                minx + 2, dataSet.getEntryPosition(entryTo) + 1), entries.size());

        float phaseX = mAnimator.getPhaseX();
        float phaseY = mAnimator.getPhaseY();

        float intensity = dataSet.getCubicIntensity();

        cubicPath.reset();

        int size = (int) Math.ceil((maxx - minx) * phaseX + minx);

        if (size - minx >= 2) {
            Entry cur = entries.get(minx);

            // let the spline start
            cubicPath.moveTo(cur.getXIndex(), cur.getVal() * phaseY);

            for (int j = minx + 1, count = Math.min(size, entries.size() - 1); j <= count; j++) {
                Entry next = entries.get(j);

                float offset = (next.getXIndex() - cur.getXIndex()) * intensity;

                cubicPath.cubicTo(next.getXIndex() - offset, cur.getVal() * phaseY, cur.getXIndex() + offset, next.getVal(), next.getXIndex(), next.getVal() * phaseY);

                cur = next;
            }
        }

        // if filled is enabled, close the path
        if (dataSet.isDrawFilledEnabled()) {

            cubicFillPath.reset();
            cubicFillPath.addPath(cubicPath);
            // create a new path, this is bad for performance
            drawCubicFill(mBitmapCanvas, dataSet, cubicFillPath, trans,
                    entryFrom.getXIndex(), entryFrom.getXIndex() + size);
        }

        mRenderPaint.setColor(dataSet.getColor());

        mRenderPaint.setStyle(Paint.Style.STROKE);

        trans.pathValueToPixel(cubicPath);

        mBitmapCanvas.drawPath(cubicPath, mRenderPaint);

        mRenderPaint.setPathEffect(null);
    }

    protected void drawCubicFill(Canvas c, LineDataSet dataSet, Path spline, Transformer trans,
                                 int from, int to) {

        if (to - from <= 1)
            return;

        float fillMin = dataSet.getFillFormatter()
                .getFillLinePosition(dataSet, mChart);

        spline.lineTo(to - 1, fillMin);
        spline.lineTo(from, fillMin);
        spline.close();

        trans.pathValueToPixel(spline);

        if (dataSet.hasGradient()) {
            drawFilledPath(c, spline, dataSet.getFillColor(), dataSet.getFillColor2(), dataSet.getFillAlpha());
        } else {
            drawFilledPath(c, spline, dataSet.getFillColor(), dataSet.getFillAlpha());
        }
    }

    /**
     * Draws a normal line.
     *
     * @param c
     * @param dataSet
     * @param entries
     */
    protected void drawLinear(Canvas c, LineDataSet dataSet, List<Entry> entries) {

        int dataSetIndex = mChart.getLineData().getIndexOfDataSet(dataSet);

        Transformer trans = mChart.getTransformer(dataSet.getAxisDependency());

        float phaseX = mAnimator.getPhaseX();
        float phaseY = mAnimator.getPhaseY();

        mRenderPaint.setStyle(Paint.Style.STROKE);

        Canvas canvas = null;

        // if the data-set is dashed, draw on bitmap-canvas
        if (dataSet.isDashedLineEnabled()) {
            canvas = mBitmapCanvas;
        } else {
            canvas = c;
        }

        Entry entryFrom = dataSet.getEntryForXIndex(mMinX);
        Entry entryTo = dataSet.getEntryForXIndex(mMaxX);

        int diff = (entryFrom == entryTo) ? 1 : 0;
        int minx = Math.max(dataSet.getEntryPosition(entryFrom) - diff, 0);
        int maxx = Math.min(Math.max(
                minx + 2, dataSet.getEntryPosition(entryTo) + 1), entries.size());

        int range = (maxx - minx) * 4 - 4;

        LineBuffer buffer = mLineBuffers[dataSetIndex];
        buffer.setPhases(phaseX, phaseY);
        buffer.limitFrom(minx);
        buffer.limitTo(maxx);
        buffer.feed(entries);

        trans.pointValuesToPixel(buffer.buffer);

        // more than 1 color
        if (dataSet.getColors().size() > 1) {

            for (int j = 0; j < range; j += 4) {

                if (!mViewPortHandler.isInBoundsRight(buffer.buffer[j]))
                    break;

                // make sure the lines don't do shitty things outside
                // bounds
                if (!mViewPortHandler.isInBoundsLeft(buffer.buffer[j + 2])
                        || (!mViewPortHandler.isInBoundsTop(buffer.buffer[j + 1]) && !mViewPortHandler
                        .isInBoundsBottom(buffer.buffer[j + 3]))
                        || (!mViewPortHandler.isInBoundsTop(buffer.buffer[j + 1]) && !mViewPortHandler
                        .isInBoundsBottom(buffer.buffer[j + 3])))
                    continue;

                // get the color that is set for this line-segment
                mRenderPaint.setColor(dataSet.getColor(j / 4 + minx));

                canvas.drawLine(buffer.buffer[j], buffer.buffer[j + 1],
                        buffer.buffer[j + 2], buffer.buffer[j + 3], mRenderPaint);
            }

        } else { // only one color per dataset

            mRenderPaint.setColor(dataSet.getColor());

            // c.drawLines(buffer.buffer, mRenderPaint);
            canvas.drawLines(buffer.buffer, 0, range,
                    mRenderPaint);
        }

        mRenderPaint.setPathEffect(null);

        // if drawing filled is enabled
        if (dataSet.isDrawFilledEnabled() && entries.size() > 0) {
            drawLinearFill(c, dataSet, entries, minx, maxx, trans);
        }
    }

    protected void drawLinearFill(Canvas c, LineDataSet dataSet, List<Entry> entries, int minx,
                                  int maxx,
                                  Transformer trans) {

        Path filled = generateFilledPath(
                entries,
                dataSet.getFillFormatter().getFillLinePosition(dataSet, mChart), minx, maxx);

        trans.pathValueToPixel(filled);

        drawFilledPath(c, filled, dataSet.getFillColor(), dataSet.getFillAlpha());
    }

    /**
     * Draws the provided path in filled mode with the provided color and alpha.
     * Special thanks to Angelo Suzuki (https://github.com/tinsukE) for this.
     *
     * @param c
     * @param filledPath
     * @param fillColor
     * @param fillAlpha
     */
    protected void drawFilledPath(Canvas c, Path filledPath, int fillColor, int fillAlpha) {
        c.save();
        c.clipPath(filledPath);

        int color = (fillAlpha << 24) | (fillColor & 0xffffff);
        c.drawColor(color);
        c.restore();
    }

    protected void drawFilledPath(Canvas c, Path filledPath, int fillColor, int fillColor2, int fillAlpha) {
        c.save();
        c.clipPath(filledPath);

        mRenderPaint.setDither(true);
        mRenderPaint.setShader(new LinearGradient(0, 0, 0, mChart.getHeight(), fillColor, fillColor2, Shader.TileMode.MIRROR));

        c.drawPath(filledPath, mRenderPaint);

        mRenderPaint.setShader(null);
        c.restore();
    }

    /**
     * Generates the path that is used for filled drawing.
     *
     * @param entries
     * @return
     */
    private Path generateFilledPath(List<Entry> entries, float fillMin, int from, int to) {

        float phaseX = mAnimator.getPhaseX();
        float phaseY = mAnimator.getPhaseY();

        Path filled = new Path();
        filled.moveTo(entries.get(from).getXIndex(), fillMin);
        filled.lineTo(entries.get(from).getXIndex(), entries.get(from).getVal() * phaseY);

        // create a new path
        for (int x = from + 1, count = (int) Math.ceil((to - from) * phaseX + from); x < count; x++) {

            Entry e = entries.get(x);
            filled.lineTo(e.getXIndex(), e.getVal() * phaseY);
        }

        // close up
        filled.lineTo(
                entries.get(
                        Math.max(
                                Math.min((int) Math.ceil((to - from) * phaseX + from) - 1,
                                        entries.size() - 1), 0)).getXIndex(), fillMin);

        filled.close();

        return filled;
    }

    @Override
    public void drawValues(Canvas c) {

        if (mChart.getLineData().getYValCount() < mChart.getMaxVisibleCount()
                * mViewPortHandler.getScaleX()) {

            List<LineDataSet> dataSets = mChart.getLineData().getDataSets();

            for (int i = 0; i < dataSets.size(); i++) {

                LineDataSet dataSet = dataSets.get(i);

                if (!dataSet.isDrawValuesEnabled() || dataSet.getEntryCount() == 0)
                    continue;

                // apply the text-styling defined by the DataSet
                applyValueTextStyle(dataSet);

                Transformer trans = mChart.getTransformer(dataSet.getAxisDependency());

                // make sure the values do not interfear with the circles
                int valOffset = (int) (dataSet.getCircleSize() * 1.75f);

                if (!dataSet.isDrawCirclesEnabled())
                    valOffset = valOffset / 2;

                List<Entry> entries = dataSet.getYVals();

                Entry entryFrom = dataSet.getEntryForXIndex(mMinX);
                Entry entryTo = dataSet.getEntryForXIndex(mMaxX);

                int diff = (entryFrom == entryTo) ? 1 : 0;
                int minx = Math.max(dataSet.getEntryPosition(entryFrom) - diff, 0);
                int maxx = Math.min(Math.max(
                        minx + 2, dataSet.getEntryPosition(entryTo) + 1), entries.size());

                float[] positions = trans.generateTransformedValuesLine(
                        entries, mAnimator.getPhaseX(), mAnimator.getPhaseY(), minx, maxx);

                for (int j = 0; j < positions.length; j += 2) {

                    float x = positions[j];
                    float y = positions[j + 1];

                    if (!mViewPortHandler.isInBoundsRight(x))
                        break;

                    if (!mViewPortHandler.isInBoundsLeft(x) || !mViewPortHandler.isInBoundsY(y))
                        continue;

                    Entry entry = entries.get(j / 2 + minx);

                    drawValue(c, dataSet.getValueFormatter(), entry.getVal(), entry, i, x,
                            y - valOffset);
                }
            }
        }
    }

    @Override
    public void drawExtras(Canvas c) {
        drawCirclesWithState(c);
    }

    protected void drawCircles(Canvas c) {

        mRenderPaint.setStyle(Paint.Style.FILL);

        float phaseX = mAnimator.getPhaseX();
        float phaseY = mAnimator.getPhaseY();

        List<LineDataSet> dataSets = mChart.getLineData().getDataSets();

        for (int i = 0; i < dataSets.size(); i++) {

            LineDataSet dataSet = dataSets.get(i);

            if (!dataSet.isVisible() || !dataSet.isDrawCirclesEnabled() ||
                    dataSet.getEntryCount() == 0)
                continue;

            Transformer trans = mChart.getTransformer(dataSet.getAxisDependency());
            List<Entry> entries = dataSet.getYVals();

            Entry entryFrom = dataSet.getEntryForXIndex((mMinX < 0) ? 0 : mMinX);
            Entry entryTo = dataSet.getEntryForXIndex(mMaxX);

            int diff = (entryFrom == entryTo) ? 1 : 0;
            int minx = Math.max(dataSet.getEntryPosition(entryFrom) - diff, 0);
            int maxx = Math.min(Math.max(
                    minx + 2, dataSet.getEntryPosition(entryTo) + 1), entries.size());

            CircleBuffer buffer = mCircleBuffers[i];
            buffer.setPhases(phaseX, phaseY);
            buffer.limitFrom(minx);
            buffer.limitTo(maxx);
            buffer.feed(entries);

            trans.pointValuesToPixel(buffer.buffer);

            float halfsize = dataSet.getCircleSize() * 4.0f / 5.0f;

            for (int j = 0, count = (int) Math.ceil((maxx - minx) * phaseX + minx) * 2; j < count; j += 2) {

                float x = buffer.buffer[j];
                float y = buffer.buffer[j + 1];

                if (!mViewPortHandler.isInBoundsRight(x))
                    break;

                // make sure the circles don't do shitty things outside
                // bounds
                if (!mViewPortHandler.isInBoundsLeft(x) || !mViewPortHandler.isInBoundsY(y))
                    continue;

                int circleColor = dataSet.getCircleColor(j / 2 + minx);

                mRenderPaint.setColor(circleColor);
                mCirclePaintInner.setColor(dataSet.getCircleHoleColor(j / 2 + minx));

                float circleSize = dataSet.getCircleSize();
                if (j >= count || count - j <= 2) {
                    circleSize = dataSet.getCircleSize() * 3.0f / 2.0f;
                    halfsize = circleSize * 4.0f / 5.0f;
                }

                c.drawCircle(x, y, circleSize,
                        mRenderPaint);

                if (dataSet.isDrawCircleHoleEnabled()
                        && circleColor != mCirclePaintInner.getColor())
                    c.drawCircle(x, y,
                            halfsize,
                            mCirclePaintInner);
            }
        }
    }

    protected void drawCirclesWithState(Canvas c) {

        mRenderPaint.setStyle(Paint.Style.FILL);

        float phaseX = mAnimator.getPhaseX();
        float phaseY = mAnimator.getPhaseY();

        List<LineDataSet> dataSets = mChart.getLineData().getDataSets();

        for (int i = 0; i < dataSets.size(); i++) {

            LineDataSet dataSet = dataSets.get(i);
            LineDataSet dataSet2 = null;
            if (i == 0)
                dataSet2 = dataSets.get(i + 1);

            if (!dataSet.isVisible() || !dataSet.isDrawCirclesEnabled() ||
                    dataSet.getEntryCount() == 0)
                continue;

            Transformer trans = mChart.getTransformer(dataSet.getAxisDependency());
            List<Entry> entries = dataSet.getYVals();

            Entry entryFrom = dataSet.getEntryForXIndex((mMinX < 0) ? 0 : mMinX);
            Entry entryTo = dataSet.getEntryForXIndex(mMaxX);

            int diff = (entryFrom == entryTo) ? 1 : 0;
            int minx = Math.max(dataSet.getEntryPosition(entryFrom) - diff, 0);
            int maxx = Math.min(Math.max(
                    minx + 2, dataSet.getEntryPosition(entryTo) + 1), entries.size());

            CircleBuffer buffer = mCircleBuffers[i];
            buffer.setPhases(phaseX, phaseY);
            buffer.limitFrom(minx);
            buffer.limitTo(maxx);
            buffer.feed(entries);

            trans.pointValuesToPixel(buffer.buffer);

            float halfsize = dataSet.getCircleSize() * 4.0f / 5.0f;

            for (int j = 0, count = (int) Math.ceil((maxx - minx) * phaseX + minx) * 2; j < count; j += 2) {

                float x = buffer.buffer[j];
                float y = buffer.buffer[j + 1];

                if (!mViewPortHandler.isInBoundsRight(x))
                    break;

                // make sure the circles don't do shitty things outside
                // bounds
                if (!mViewPortHandler.isInBoundsLeft(x) || !mViewPortHandler.isInBoundsY(y))
                    continue;

                int circleColor = dataSet.getCircleColor(j / 2 + minx);

                mRenderPaint.setColor(circleColor);
                mCirclePaintInner.setColor(dataSet.getCircleHoleColor(j / 2 + minx));
                if (dataSet2 != null && i == 0 && dataSet.getYValForXIndex(j / 2 + minx) >= dataSet2.getYValForXIndex(j / 2 + minx)) {
                    mCirclePaintInner.setColor(dataSet.getSuccessColor());
                } else if (dataSet2 != null && i == 0 && dataSet.getYValForXIndex(j / 2 + minx) < dataSet2.getYValForXIndex(j / 2 + minx)) {
                    mCirclePaintInner.setColor(dataSet.getFailedColor());
                } else {
                    mCirclePaintInner.setColor(dataSet.getFailedColor());
                }

                float circleSize = dataSet.getCircleSize();
                if (j >= count || count - j <= 2) {
                    circleSize = dataSet.getCircleSize() * 3.0f / 2.0f;
                    halfsize = circleSize * 4.0f / 5.0f;
                }

                if (dataSet.getInitPoint()) {
                    dataSet.setInitPoint(false);
                } else {
                    c.drawCircle(x, y, circleSize,
                            mRenderPaint);

                    if (dataSet.isDrawCircleHoleEnabled()
                            && circleColor != mCirclePaintInner.getColor())
                        c.drawCircle(x, y,
                                halfsize,
                                mCirclePaintInner);
                }
            }
        }
    }

    @Override
    public void drawHighlighted(Canvas c, Highlight[] indices) {

        for (int i = 0; i < indices.length; i++) {

            LineDataSet set = mChart.getLineData().getDataSetByIndex(indices[i]
                    .getDataSetIndex());

            if (set == null || !set.isHighlightEnabled())
                continue;

            int xIndex = indices[i].getXIndex(); // get the
            // x-position

            if (xIndex > mChart.getXChartMax() * mAnimator.getPhaseX())
                continue;

            final float yVal = set.getYValForXIndex(xIndex);
            if (yVal == Float.NaN)
                continue;

            float y = yVal * mAnimator.getPhaseY(); // get
            // the
            // y-position

            float[] pts = new float[]{
                    xIndex, y
            };

            mChart.getTransformer(set.getAxisDependency()).pointValuesToPixel(pts);

            // draw the lines
            drawHighlightLines(c, pts, set);
        }
    }

    /**
     * Releases the drawing bitmap. This should be called when {@link LineChart#onDetachedFromWindow()}.
     */
    public void releaseBitmap() {
        if (mDrawBitmap != null) {
            mDrawBitmap.recycle();
            mDrawBitmap = null;
        }
    }
}
