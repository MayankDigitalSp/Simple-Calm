package com.mnx.still;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private MeditationView meditationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(243, 240, 232));
        window.setNavigationBarColor(Color.rgb(243, 240, 232));
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        meditationView = new MeditationView(this);
        setContentView(meditationView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (meditationView != null) meditationView.syncTimer();
    }

    @Override
    protected void onPause() {
        if (meditationView != null) meditationView.persistTimer();
        super.onPause();
    }

    static class MeditationView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final SharedPreferences prefs;
        private final int background = Color.rgb(243, 240, 232);
        private final int green = Color.rgb(72, 106, 90);
        private final int dark = Color.rgb(35, 45, 40);
        private final int muted = Color.rgb(117, 124, 116);
        private final int card = Color.rgb(251, 249, 244);
        private final int track = Color.rgb(218, 220, 210);

        private final int[] presets = {5, 10, 15, 20};
        private int selectedMinutes = 10;
        private long remainingMs = selectedMinutes * 60_000L;
        private long endAtMs = 0L;
        private boolean running = false;
        private CountDownTimer timer;

        private int sessions;
        private int totalMinutes;
        private int streak;

        private final RectF startButton = new RectF();
        private final RectF resetButton = new RectF();
        private final RectF[] presetButtons = {new RectF(), new RectF(), new RectF(), new RectF()};

        MeditationView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            prefs = context.getSharedPreferences("still_stats", Context.MODE_PRIVATE);
            sessions = prefs.getInt("sessions", 0);
            totalMinutes = prefs.getInt("total_minutes", 0);
            streak = prefs.getInt("streak", 0);
            selectedMinutes = prefs.getInt("selected_minutes", 10);
            remainingMs = selectedMinutes * 60_000L;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(background);
            float w = getWidth();
            float density = getResources().getDisplayMetrics().density;

            text(canvas, "STILL", w / 2, 52 * density, 15 * density, green, Paint.Align.CENTER, true);
            text(canvas, "Take a moment for yourself", w / 2, 78 * density, 14 * density, muted, Paint.Align.CENTER, false);

            float cx = w / 2;
            float cy = 238 * density;
            float radius = Math.min(w * .34f, 132 * density);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(9 * density);
            paint.setColor(track);
            canvas.drawCircle(cx, cy, radius, paint);

            float totalMs = selectedMinutes * 60_000f;
            float progress = totalMs == 0 ? 0 : 1f - (remainingMs / totalMs);
            paint.setColor(green);
            RectF arc = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(arc, -90, 360 * progress, false, paint);
            paint.setStyle(Paint.Style.FILL);

            long seconds = Math.max(0, (remainingMs + 999) / 1000);
            String time = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
            text(canvas, time, cx, cy + 10 * density, 47 * density, dark, Paint.Align.CENTER, false);
            text(canvas, running ? "BREATHE SLOWLY" : "READY WHEN YOU ARE", cx, cy + 43 * density, 10 * density, muted, Paint.Align.CENTER, true);

            float chipY = 400 * density;
            float gap = 9 * density;
            float chipW = Math.min(65 * density, (w - 48 * density - gap * 3) / 4);
            float totalW = chipW * 4 + gap * 3;
            float chipX = (w - totalW) / 2;
            for (int i = 0; i < presets.length; i++) {
                RectF r = presetButtons[i];
                r.set(chipX + i * (chipW + gap), chipY, chipX + i * (chipW + gap) + chipW, chipY + 43 * density);
                boolean active = presets[i] == selectedMinutes;
                roundRect(canvas, r, 14 * density, active ? green : card, 0);
                text(canvas, presets[i] + " min", r.centerX(), r.centerY() + 5 * density, 12 * density, active ? Color.WHITE : dark, Paint.Align.CENTER, true);
            }

            float buttonY = 468 * density;
            startButton.set(w * .15f, buttonY, w * .85f, buttonY + 55 * density);
            roundRect(canvas, startButton, 18 * density, green, 5 * density);
            text(canvas, running ? "PAUSE" : (remainingMs < selectedMinutes * 60_000L ? "RESUME" : "BEGIN"), w / 2, startButton.centerY() + 6 * density, 15 * density, Color.WHITE, Paint.Align.CENTER, true);

            resetButton.set(w * .35f, buttonY + 64 * density, w * .65f, buttonY + 100 * density);
            text(canvas, "RESET", w / 2, resetButton.centerY() + 5 * density, 11 * density, muted, Paint.Align.CENTER, true);

            float statsTop = buttonY + 126 * density;
            text(canvas, "YOUR PRACTICE", 24 * density, statsTop, 11 * density, muted, Paint.Align.LEFT, true);
            float cardTop = statsTop + 18 * density;
            float cardGap = 10 * density;
            float statW = (w - 48 * density - cardGap * 2) / 3;
            drawStat(canvas, 24 * density, cardTop, statW, "" + streak, "DAY STREAK", "●");
            drawStat(canvas, 24 * density + statW + cardGap, cardTop, statW, "" + totalMinutes, "MINUTES", "◷");
            drawStat(canvas, 24 * density + (statW + cardGap) * 2, cardTop, statW, "" + sessions, "SESSIONS", "✓");
        }

        private void drawStat(Canvas canvas, float x, float y, float width, String value, String label, String icon) {
            float d = getResources().getDisplayMetrics().density;
            RectF box = new RectF(x, y, x + width, y + 104 * d);
            roundRect(canvas, box, 18 * d, card, 2 * d);
            text(canvas, icon, box.centerX(), y + 29 * d, 15 * d, green, Paint.Align.CENTER, true);
            text(canvas, value, box.centerX(), y + 61 * d, 22 * d, dark, Paint.Align.CENTER, true);
            text(canvas, label, box.centerX(), y + 84 * d, 8 * d, muted, Paint.Align.CENTER, true);
        }

        private void text(Canvas c, String value, float x, float y, float size, int color, Paint.Align align, boolean bold) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTextAlign(align);
            paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setLetterSpacing(bold ? .08f : 0f);
            c.drawText(value, x, y, paint);
        }

        private void roundRect(Canvas c, RectF rect, float radius, int color, float shadow) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            if (shadow > 0) paint.setShadowLayer(shadow, 0, shadow / 2, 0x18000000);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.clearShadowLayer();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float x = event.getX(), y = event.getY();
            for (int i = 0; i < presetButtons.length; i++) {
                if (presetButtons[i].contains(x, y) && !running) {
                    selectedMinutes = presets[i];
                    remainingMs = selectedMinutes * 60_000L;
                    prefs.edit().putInt("selected_minutes", selectedMinutes).apply();
                    invalidate();
                    return true;
                }
            }
            if (startButton.contains(x, y)) {
                if (running) pauseTimer(); else startTimer();
                return true;
            }
            if (resetButton.contains(x, y)) {
                resetTimer();
                return true;
            }
            return true;
        }

        private void startTimer() {
            if (remainingMs <= 0) remainingMs = selectedMinutes * 60_000L;
            running = true;
            endAtMs = System.currentTimeMillis() + remainingMs;
            runCountdown();
            invalidate();
        }

        private void runCountdown() {
            if (timer != null) timer.cancel();
            timer = new CountDownTimer(remainingMs, 250) {
                @Override public void onTick(long millisUntilFinished) {
                    remainingMs = Math.max(0, endAtMs - System.currentTimeMillis());
                    invalidate();
                }
                @Override public void onFinish() {
                    remainingMs = 0;
                    running = false;
                    completeSession();
                    invalidate();
                }
            }.start();
        }

        private void pauseTimer() {
            remainingMs = Math.max(0, endAtMs - System.currentTimeMillis());
            running = false;
            endAtMs = 0;
            if (timer != null) timer.cancel();
            persistTimer();
            invalidate();
        }

        private void resetTimer() {
            running = false;
            endAtMs = 0;
            if (timer != null) timer.cancel();
            remainingMs = selectedMinutes * 60_000L;
            prefs.edit().remove("end_at").remove("remaining").remove("was_running").apply();
            invalidate();
        }

        void persistTimer() {
            if (running) remainingMs = Math.max(0, endAtMs - System.currentTimeMillis());
            prefs.edit()
                    .putLong("remaining", remainingMs)
                    .putLong("end_at", endAtMs)
                    .putBoolean("was_running", running)
                    .apply();
        }

        void syncTimer() {
            if (!prefs.getBoolean("was_running", false)) return;
            long storedEnd = prefs.getLong("end_at", 0);
            long now = System.currentTimeMillis();
            if (storedEnd > now) {
                remainingMs = storedEnd - now;
                endAtMs = storedEnd;
                running = true;
                runCountdown();
            } else if (storedEnd > 0) {
                remainingMs = 0;
                running = false;
                completeSession();
            }
            invalidate();
        }

        private void completeSession() {
            prefs.edit().remove("end_at").remove("remaining").putBoolean("was_running", false).apply();
            sessions++;
            totalMinutes += selectedMinutes;
            updateStreak();
            prefs.edit()
                    .putInt("sessions", sessions)
                    .putInt("total_minutes", totalMinutes)
                    .putInt("streak", streak)
                    .apply();
            try {
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70);
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 500);
            } catch (Exception ignored) { }
            new AlertDialog.Builder(getContext())
                    .setTitle("Session complete")
                    .setMessage("You gave yourself " + selectedMinutes + " mindful minutes.")
                    .setPositiveButton("Done", (dialog, which) -> resetTimer())
                    .setCancelable(false)
                    .show();
        }

        private void updateStreak() {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String today = fmt.format(new Date());
            String last = prefs.getString("last_session_date", "");
            if (today.equals(last)) return;
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            String yesterdayText = fmt.format(yesterday.getTime());
            streak = yesterdayText.equals(last) ? streak + 1 : 1;
            prefs.edit().putString("last_session_date", today).apply();
        }
    }
}
