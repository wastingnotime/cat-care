package care.cat.offline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Local vector face with distinguishable coat colors and markings. */
final class CatAvatar extends View {
  private final CatCoat coat;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path shape = new Path();

  CatAvatar(Context context, String id) {
    super(context);
    coat = CatCoat.fromId(id);
    setContentDescription("Pelagem " + coat.label);
  }

  private void oval(Canvas canvas, int color, float l, float t, float r, float b) {
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(color);
    canvas.drawOval(l, t, r, b, paint);
  }

  private void ear(Canvas canvas, boolean right, int color) {
    shape.reset();
    shape.moveTo(right ? 58 : 16, 43);
    shape.lineTo(right ? 84 : 16, 12);
    shape.lineTo(right ? 86 : 42, 46);
    shape.close();
    paint.setColor(color);
    canvas.drawPath(shape, paint);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float scale = Math.min(getWidth(), getHeight()) / 100f;
    canvas.save();
    canvas.translate((getWidth() - 100 * scale) / 2, (getHeight() - 100 * scale) / 2);
    canvas.scale(scale, scale);
    oval(canvas, 0xffe9eee4, 0, 0, 100, 100);
    int ears = coat == CatCoat.SIAMESE ? 0xff594c48 : coat.color;
    ear(canvas, false, ears);
    ear(canvas, true, ears);
    oval(canvas, coat.color, 12, 25, 88, 91);
    if (coat == CatCoat.CALICO) {
      oval(canvas, 0xff303534, 16, 28, 45, 56);
      oval(canvas, 0xffd78338, 57, 30, 85, 66);
    }
    if (coat == CatCoat.ORANGE_TABBY || coat == CatCoat.BROWN_TABBY) {
      paint.setColor(coat == CatCoat.ORANGE_TABBY ? 0xff9e5e26 : 0xff4f4338);
      paint.setStrokeWidth(4);
      paint.setStrokeCap(Paint.Cap.ROUND);
      canvas.drawLine(39, 31, 44, 44, paint);
      canvas.drawLine(50, 29, 50, 43, paint);
      canvas.drawLine(61, 31, 56, 44, paint);
    }
    if (coat == CatCoat.TUXEDO) oval(canvas, 0xfff4f0e6, 32, 60, 68, 88);
    if (coat == CatCoat.SIAMESE) oval(canvas, 0xff594c48, 29, 52, 71, 89);
    int eyes =
        coat == CatCoat.BLACK || coat == CatCoat.TUXEDO
            ? 0xffd9c36b
            : coat == CatCoat.SIAMESE ? 0xff4c91a3 : 0xff243c32;
    oval(canvas, eyes, 29, 54, 37, 62);
    oval(canvas, eyes, 63, 54, 71, 62);
    oval(canvas, 0xffc48b8b, 45, 66, 55, 72);
    paint.setColor(coat == CatCoat.BLACK ? 0xffd8d0bd : 0xff243c32);
    paint.setStrokeWidth(2);
    canvas.drawLine(50, 72, 50, 77, paint);
    canvas.drawLine(50, 77, 44, 80, paint);
    canvas.drawLine(50, 77, 56, 80, paint);
    canvas.restore();
  }
}
