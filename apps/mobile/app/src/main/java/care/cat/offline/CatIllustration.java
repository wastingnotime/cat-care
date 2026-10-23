package care.cat.offline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Decorative vector artwork; scales cleanly without downloaded image assets. */
final class CatIllustration extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path stroke = new Path(), tail = new Path(), ears = new Path();

  CatIllustration(Context context) {
    super(context);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  private void fill(Canvas canvas, int color, float left, float top, float right, float bottom) {
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(color);
    canvas.drawOval(left, top, right, bottom, paint);
  }

  private void line(Canvas canvas, int color, float width, float... points) {
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(color);
    paint.setStrokeWidth(width);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    Path path = stroke;
    path.reset();
    path.moveTo(points[0], points[1]);
    for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
    canvas.drawPath(path, paint);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float scale = Math.min(getWidth() / 260f, getHeight() / 180f);
    canvas.save();
    canvas.translate((getWidth() - 260 * scale) / 2, (getHeight() - 180 * scale) / 2);
    canvas.scale(scale, scale);
    fill(canvas, 0xffdce7d7, 37, 12, 220, 174);
    fill(canvas, 0xffc6d6c1, 38, 143, 224, 165);
    fill(canvas, 0xffd6ab80, 87, 65, 221, 155);
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(0xffb78054);
    paint.setStrokeWidth(22);
    paint.setStrokeCap(Paint.Cap.ROUND);
    tail.reset();
    tail.moveTo(199, 111);
    tail.cubicTo(248, 149, 186, 174, 143, 147);
    canvas.drawPath(tail, paint);
    ears.reset();
    ears.moveTo(52, 104);
    ears.lineTo(45, 44);
    ears.lineTo(83, 67);
    ears.lineTo(110, 60);
    ears.lineTo(137, 40);
    ears.lineTo(141, 108);
    ears.close();
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(0xffe4bd96);
    canvas.drawPath(ears, paint);
    fill(canvas, 0xffe4bd96, 46, 68, 146, 145);
    line(canvas, 0xffbc8d65, 5, 56, 61, 62, 78);
    line(canvas, 0xffbc8d65, 5, 128, 59, 126, 76);
    line(canvas, 0xff334e40, 3, 64, 104, 71, 108, 78, 104);
    line(canvas, 0xff334e40, 3, 109, 104, 116, 108, 123, 104);
    fill(canvas, 0xffaf745b, 89, 113, 99, 119);
    line(canvas, 0xff334e40, 2, 94, 119, 94, 123, 88, 126);
    line(canvas, 0xff334e40, 2, 94, 123, 100, 126);
    line(canvas, 0xffbc8d65, 2, 54, 117, 34, 113);
    line(canvas, 0xffbc8d65, 2, 55, 123, 36, 126);
    line(canvas, 0xffbc8d65, 2, 133, 117, 151, 113);
    fill(canvas, 0xffedd0af, 64, 138, 107, 155);
    line(canvas, 0xff75957b, 3, 29, 84, 29, 96);
    line(canvas, 0xff75957b, 3, 23, 90, 35, 90);
    fill(canvas, 0xffc99171, 205, 39, 214, 48);
    canvas.restore();
  }
}
