package org.tpmkranz.tsp;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.CoordinatorLayout.LayoutParams;
import android.support.design.widget.FloatingActionButton;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class TopChildBottomMarginBehavior extends FloatingActionButton.Behavior {

  public TopChildBottomMarginBehavior(Context c, AttributeSet s) {
    super(c, s);
  }

  @Override
  public boolean onLayoutChild(CoordinatorLayout parent, FloatingActionButton child,
      int layoutDirection) {
    LayoutParams p = (LayoutParams) child.getLayoutParams();
    try {
      View anchor = parent.findViewById(p.getAnchorId());
      if (anchor.getVisibility() == View.VISIBLE) {
        child.setTranslationY(
            -parent.getResources().getDimensionPixelSize(R.dimen.activity_vertical_margin)
        );
      } else {
        Log.d("ANCHOR VISIBILITY", String.valueOf(anchor.getVisibility()));
        child.setTranslationY(
            anchor.getHeight() / 2.f
        );
      }
    } catch (NullPointerException e) {

    }
    return super.onLayoutChild(parent, child, layoutDirection);
  }
}
