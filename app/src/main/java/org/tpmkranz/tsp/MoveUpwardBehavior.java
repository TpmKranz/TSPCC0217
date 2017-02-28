package org.tpmkranz.tsp;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;

/**
 * Moves an arbitrary view upwards if it threatens to collide with a {@link Snackbar}.
 *
 * <p>Courtesy of <a href="http://stackoverflow.com/a/35904421"></a>
 */
public class MoveUpwardBehavior extends CoordinatorLayout.Behavior<View> {
  public MoveUpwardBehavior() {
    super();
  }

  public MoveUpwardBehavior(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) {
    return dependency instanceof Snackbar.SnackbarLayout;
  }

  @Override
  public boolean onDependentViewChanged(CoordinatorLayout parent, View child, View dependency) {
    float translationY = Math.min(0, ViewCompat.getTranslationY(dependency) - dependency.getHeight());
    ViewCompat.animate(child).cancel();
    ViewCompat.setTranslationY(child, translationY);
    return true;
  }

  @Override
  public void onDependentViewRemoved(CoordinatorLayout parent, View child, View dependency) {
    ViewCompat.animate(child).translationY(0).start();
  }
}
