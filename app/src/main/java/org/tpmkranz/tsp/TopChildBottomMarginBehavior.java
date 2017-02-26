package org.tpmkranz.tsp;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.util.AttributeSet;
import android.util.Log;

public class TopChildBottomMarginBehavior extends FloatingActionButton.Behavior {

  public TopChildBottomMarginBehavior(Context c, AttributeSet s) {
    super(c, s);
  }

  @Override
  public boolean onLayoutChild(CoordinatorLayout parent, FloatingActionButton child,
      int layoutDirection) {
    child.setTranslationY(
        -parent.getResources().getDimensionPixelSize(R.dimen.activity_vertical_margin)
    );
    return super.onLayoutChild(parent, child, layoutDirection);
  }
}
