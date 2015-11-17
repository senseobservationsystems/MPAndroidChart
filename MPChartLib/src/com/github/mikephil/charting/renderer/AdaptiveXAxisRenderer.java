package com.github.mikephil.charting.renderer;

import android.graphics.Canvas;
import android.graphics.Path;
import android.util.Log;

import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.List;

/**
 * Created by Muhammad Johan Alibasa on 11/16/15.
 */
public class AdaptiveXAxisRenderer extends XAxisRenderer {

    protected LineData mLineData;
    protected float mPhaseY = 1.0f;

    public AdaptiveXAxisRenderer(ViewPortHandler viewPortHandler, XAxis xAxis, Transformer trans) {
        super(viewPortHandler, xAxis, trans);
    }

    public void setLineData(LineData lineData) {
        this.mLineData = lineData;
    }

    public void setPhaseY(float phaseY) {
        this.mPhaseY = phaseY;
    }

    @Override
    public void renderGridLines(Canvas c) {

        if (!mXAxis.isDrawGridLinesEnabled() || !mXAxis.isEnabled())
            return;

        // pre alloc
        float[] position = new float[] {
                0f, 0f
        };

        mGridPaint.setColor(mXAxis.getGridColor());
        mGridPaint.setStrokeWidth(mXAxis.getGridLineWidth());
        mGridPaint.setPathEffect(mXAxis.getGridDashPathEffect());

        Path gridLinePath = new Path();

        List<LineDataSet> dataSets = mLineData.getDataSets();
        if (dataSets.size() < 1) // return if the line data is 0
            return;

        float[] posYArray = new float[dataSets.get(0).getEntryCount()];
        for (int i = 0; i < posYArray.length; i++) { posYArray[i] = 0; }

        for (int i = 0; i < dataSets.size(); i++) {
            List<Entry> entries = dataSets.get(i).getYVals();
            for (int j = 0; j < entries.size(); j++) {
                float valueY = entries.get(j).getVal() * this.mPhaseY;
                if (posYArray[j] < valueY) {
                    posYArray[j] = valueY;
                }
            }
        }

        for (int i = 0; i < dataSets.get(0).getEntryCount(); i += mXAxis.mAxisLabelModulus) {

            position[0] = i;
            position[1] = posYArray[i];
            mTrans.pointValuesToPixel(position);

            if (position[0] >= mViewPortHandler.offsetLeft()
                    && position[0] <= mViewPortHandler.getChartWidth()) {

                gridLinePath.moveTo(position[0], mViewPortHandler.contentBottom());
                gridLinePath.lineTo(position[0], position[1]);

                // draw a path because lines don't support dashing on lower android versions
                c.drawPath(gridLinePath, mGridPaint);
            }

            gridLinePath.reset();
        }
    }
}
