package com.local.exchangecalc;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Base64;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class MainActivity extends Activity {
    private ExchangeView exchangeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(8, 88, 255));
        window.setNavigationBarColor(Color.rgb(240, 243, 248));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        exchangeView = new ExchangeView(this);
        setContentView(exchangeView);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    public static class ExchangeView extends View {
        private static final float TEMPLATE_W = 709f;
        private static final float TEMPLATE_H = 1482f;
        private static final long MULTITAP_WINDOW_MS = 3000L;
        private static final int REQUIRED_TAPS = 5;
        private static final long STABILIZE_MS = 220L;

        private static final long DEFAULT_JPY = 852192L;
        private static final double DEFAULT_CNY = 36672.38;
        private static final double DEFAULT_SAVING = 253.10;

        private static final String PREFS = "exchange_demo_v2";
        private static final String KEY_JPY = "jpy";
        private static final String KEY_CNY = "cny";
        private static final String KEY_SAVING = "saving";

        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint amountPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint disclaimerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final SharedPreferences prefs;
        private final Bitmap template;

        private Data savedData;
        private Data workingData;
        private int tapCount = 0;
        private long firstTapAt = 0L;

        private final DecimalFormat jpyFormat;
        private final DecimalFormat moneyFormat;
        private final DecimalFormat rateFormat;

        public ExchangeView(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setBackgroundColor(Color.rgb(240, 243, 248));

            byte[] raw = Base64.decode(TemplatePart0.DATA + TemplatePart1.DATA + TemplatePart2.DATA + TemplatePart3.DATA + TemplatePart4.DATA + TemplatePart5.DATA, Base64.DEFAULT);
            template = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

            DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
            jpyFormat = new DecimalFormat("#,##0", symbols);
            moneyFormat = new DecimalFormat("#,##0.00", symbols);
            rateFormat = new DecimalFormat("0.000000", symbols);

            amountPaint.setColor(Color.rgb(48, 48, 51));
            amountPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            amountPaint.setTextAlign(Paint.Align.RIGHT);

            smallPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            smallPaint.setTextSize(18f);
            smallPaint.setTextAlign(Paint.Align.LEFT);

            disclaimerPaint.setColor(Color.rgb(112, 117, 126));
            disclaimerPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            disclaimerPaint.setTextAlign(Paint.Align.CENTER);
            disclaimerPaint.setTextSize(12f);

            savedData = loadSaved();
            workingData = savedData.copy();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float sx = getWidth() / TEMPLATE_W;
            float sy = getHeight() / TEMPLATE_H;
            canvas.save();
            canvas.scale(sx, sy);
            canvas.drawBitmap(template, null, new RectF(0, 0, TEMPLATE_W, TEMPLATE_H), bitmapPaint);

            drawAmount(canvas, jpyFormat.format(workingData.jpy), 662f, 311f, 51f, 265f);
            drawAmount(canvas, moneyFormat.format(workingData.cny), 662f, 444f, 51f, 285f);
            drawMarketLine(canvas, workingData.marketPrice(), workingData.saving);

            canvas.drawText("非官方", TEMPLATE_W / 2f, 1476f, disclaimerPaint);
            canvas.restore();
        }

        private void drawAmount(Canvas canvas, String text, float rightX, float baselineY, float baseSize, float maxWidth) {
            amountPaint.setTextSize(baseSize);
            while (amountPaint.measureText(text) > maxWidth && amountPaint.getTextSize() > 39f) {
                amountPaint.setTextSize(amountPaint.getTextSize() - 1f);
            }
            canvas.drawText(text, rightX, baselineY, amountPaint);
        }

        private void drawMarketLine(Canvas canvas, double marketPrice, double saving) {
            String market = moneyFormat.format(marketPrice);
            String save = moneyFormat.format(saving);
            String before = market;
            String mid = "省";
            String end = save + "元";

            smallPaint.setTextSize(18f);
            float wBefore = smallPaint.measureText(before);
            float wMid = smallPaint.measureText(mid);
            float wEnd = smallPaint.measureText(end);
            float gap = 2f;
            float total = wBefore + gap + wMid + gap + wEnd;
            float x = 622f - total;
            float y = 523f;

            smallPaint.setColor(Color.rgb(116, 120, 128));
            canvas.drawText(before, x, y, smallPaint);
            float strikeY = y - 6f;
            canvas.drawLine(x, strikeY, x + wBefore, strikeY, smallPaint);
            x += wBefore + gap;

            smallPaint.setColor(Color.rgb(60, 62, 67));
            canvas.drawText(mid, x, y, smallPaint);
            x += wMid + gap;

            smallPaint.setColor(Color.rgb(240, 101, 73));
            canvas.drawText(save, x, y, smallPaint);
            x += smallPaint.measureText(save);

            smallPaint.setColor(Color.rgb(60, 62, 67));
            canvas.drawText("元", x, y, smallPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float tx = event.getX() * TEMPLATE_W / Math.max(1f, getWidth());
            float ty = event.getY() * TEMPLATE_H / Math.max(1f, getHeight());
            boolean inSecretArea = tx >= 545f && tx <= 635f && ty >= 20f && ty <= 95f;
            if (!inSecretArea) {
                tapCount = 0;
                firstTapAt = 0L;
                return true;
            }

            long now = System.currentTimeMillis();
            if (tapCount == 0 || now - firstTapAt > MULTITAP_WINDOW_MS) {
                tapCount = 1;
                firstTapAt = now;
            } else {
                tapCount++;
            }
            if (tapCount >= REQUIRED_TAPS) {
                tapCount = 0;
                firstTapAt = 0L;
                openEditor();
            }
            return true;
        }

        private void openEditor() {
            final Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout root = new LinearLayout(getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(20), dp(18), dp(20), dp(18));
            root.setBackgroundColor(Color.WHITE);

            TextView title = text("编辑展示数据", 20, Color.rgb(34, 36, 40));
            title.setTypeface(Typeface.DEFAULT_BOLD);
            root.addView(title, lpMatchWrap(0, dp(12)));

            EditText jpyInput = field(root, "日元金额 JPY", String.valueOf(workingData.jpy), true);
            EditText cnyInput = field(root, "人民币金额 CNY", moneyPlain(workingData.cny), false);
            EditText saveInput = field(root, "节省金额", moneyPlain(workingData.saving), false);

            TextView derived = text("", 13, Color.rgb(104, 109, 119));
            derived.setPadding(0, dp(5), 0, dp(8));
            root.addView(derived, lpMatchWrap(0, dp(6)));

            Runnable updateDerived = () -> {
                Data d = parseFields(jpyInput, cnyInput, saveInput, false);
                if (d == null) {
                    derived.setText("请输入有效数字。日元范围：1 ～ 4,000,000；节省金额不能为负数。");
                } else {
                    derived.setText("成交汇率（100日元） " + rateFormat.format(d.dealRate())
                            + "    市场价 " + moneyFormat.format(d.marketPrice())
                            + "\n市场汇率（100日元） " + rateFormat.format(d.marketRate()));
                }
            };
            TextWatcher watcher = new SimpleWatcher(updateDerived);
            jpyInput.addTextChangedListener(watcher);
            cnyInput.addTextChangedListener(watcher);
            saveInput.addTextChangedListener(watcher);
            updateDerived.run();

            LinearLayout buttons = new LinearLayout(getContext());
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.CENTER_VERTICAL);

            Button reset = button("恢复默认");
            Button preview = button("预览");
            Button save = button("保存");
            buttons.addView(reset, new LinearLayout.LayoutParams(0, dp(48), 1f));
            LinearLayout.LayoutParams gapLp = new LinearLayout.LayoutParams(dp(8), 1);
            View gap1 = new View(getContext());
            buttons.addView(gap1, gapLp);
            buttons.addView(preview, new LinearLayout.LayoutParams(0, dp(48), 1f));
            View gap2 = new View(getContext());
            buttons.addView(gap2, new LinearLayout.LayoutParams(dp(8), 1));
            buttons.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1f));
            root.addView(buttons, lpMatchWrap(dp(8), 0));

            TextView note = text("预览不会覆盖上一次正式保存的数据。保存后，下次打开仍显示本次结果。", 12, Color.rgb(137, 143, 153));
            note.setPadding(0, dp(10), 0, 0);
            root.addView(note, lpMatchWrap(0, 0));

            reset.setOnClickListener(v -> {
                jpyInput.setText(String.valueOf(DEFAULT_JPY));
                cnyInput.setText(moneyPlain(DEFAULT_CNY));
                saveInput.setText(moneyPlain(DEFAULT_SAVING));
                updateDerived.run();
            });

            preview.setOnClickListener(v -> {
                Data d = parseFields(jpyInput, cnyInput, saveInput, true);
                if (d == null) return;
                workingData = d;
                closeEditor(dialog);
            });

            save.setOnClickListener(v -> {
                Data d = parseFields(jpyInput, cnyInput, saveInput, true);
                if (d == null) return;
                workingData = d;
                savedData = d.copy();
                prefs.edit()
                        .putLong(KEY_JPY, savedData.jpy)
                        .putString(KEY_CNY, Double.toString(savedData.cny))
                        .putString(KEY_SAVING, Double.toString(savedData.saving))
                        .apply();
                closeEditor(dialog);
            });

            dialog.setContentView(root);
            Window w = dialog.getWindow();
            if (w != null) {
                WindowManager.LayoutParams p = new WindowManager.LayoutParams();
                p.copyFrom(w.getAttributes());
                p.width = ViewGroup.LayoutParams.MATCH_PARENT;
                p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                p.gravity = Gravity.BOTTOM;
                w.setAttributes(p);
                w.setBackgroundDrawableResource(android.R.color.transparent);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            dialog.show();
            w = dialog.getWindow();
            if (w != null) {
                WindowManager.LayoutParams p = w.getAttributes();
                p.width = ViewGroup.LayoutParams.MATCH_PARENT;
                p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                p.gravity = Gravity.BOTTOM;
                w.setAttributes(p);
            }
        }

        private void closeEditor(Dialog dialog) {
            hideKeyboard();
            clearFocus();
            dialog.dismiss();
            handler.postDelayed(() -> {
                requestFocus();
                invalidate();
            }, STABILIZE_MS);
        }

        private EditText field(LinearLayout root, String label, String value, boolean integerOnly) {
            TextView l = text(label, 13, Color.rgb(91, 96, 106));
            root.addView(l, lpMatchWrap(dp(2), dp(5)));
            EditText e = new EditText(getContext());
            e.setSingleLine(true);
            e.setText(value);
            e.setTextSize(18);
            e.setSelectAllOnFocus(true);
            e.setPadding(dp(12), 0, dp(12), 0);
            if (integerOnly) {
                e.setInputType(InputType.TYPE_CLASS_NUMBER);
            } else {
                e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            }
            root.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            return e;
        }

        private Button button(String s) {
            Button b = new Button(getContext());
            b.setText(s);
            b.setTextSize(14);
            b.setAllCaps(false);
            return b;
        }

        private TextView text(String s, int sp, int color) {
            TextView t = new TextView(getContext());
            t.setText(s);
            t.setTextSize(sp);
            t.setTextColor(color);
            return t;
        }

        private LinearLayout.LayoutParams lpMatchWrap(int top, int bottom) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = top;
            lp.bottomMargin = bottom;
            return lp;
        }

        private Data parseFields(EditText jpy, EditText cny, EditText saving, boolean showError) {
            try {
                long j = Long.parseLong(jpy.getText().toString().replace(",", "").trim());
                double c = Double.parseDouble(cny.getText().toString().replace(",", "").trim());
                double s = Double.parseDouble(saving.getText().toString().replace(",", "").trim());
                if (j < 1L || j > 4000000L || c < 0d || s < 0d || Double.isNaN(c) || Double.isNaN(s) || Double.isInfinite(c) || Double.isInfinite(s)) {
                    throw new IllegalArgumentException();
                }
                return new Data(j, round2(c), round2(s));
            } catch (Exception ex) {
                if (showError) {
                    if (jpy.getText().toString().trim().isEmpty()) jpy.setError("请输入日元金额");
                    else cny.setError("请检查金额格式或范围");
                }
                return null;
            }
        }

        private Data loadSaved() {
            long j = prefs.getLong(KEY_JPY, DEFAULT_JPY);
            double c = parseStoredDouble(prefs.getString(KEY_CNY, null), DEFAULT_CNY);
            double s = parseStoredDouble(prefs.getString(KEY_SAVING, null), DEFAULT_SAVING);
            if (j < 1L || j > 4000000L || c < 0 || s < 0) return new Data(DEFAULT_JPY, DEFAULT_CNY, DEFAULT_SAVING);
            return new Data(j, round2(c), round2(s));
        }

        private double parseStoredDouble(String v, double fallback) {
            if (v == null) return fallback;
            try { return Double.parseDouble(v); } catch (Exception e) { return fallback; }
        }

        private void hideKeyboard() {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }

        private String moneyPlain(double n) {
            return String.format(Locale.US, "%.2f", n);
        }

        private double round2(double n) {
            return Math.round(n * 100d) / 100d;
        }

        private int dp(int v) {
            float d = getResources().getDisplayMetrics().density;
            return Math.round(v * d);
        }

        private static class Data {
            final long jpy;
            final double cny;
            final double saving;
            Data(long jpy, double cny, double saving) {
                this.jpy = jpy;
                this.cny = cny;
                this.saving = saving;
            }
            double dealRate() { return cny / jpy * 100d; }
            double marketPrice() { return cny + saving; }
            double marketRate() { return marketPrice() / jpy * 100d; }
            Data copy() { return new Data(jpy, cny, saving); }
        }

        private static class SimpleWatcher implements TextWatcher {
            private final Runnable r;
            SimpleWatcher(Runnable r) { this.r = r; }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { r.run(); }
            public void afterTextChanged(Editable s) {}
        }
    }
}
