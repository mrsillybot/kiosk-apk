package dk.mrsilly.kiosk;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {
    private static final String[] DAYS = { "Man", "Tir", "Ons", "Tor", "Fre", "Lør", "Søn" };

    /** The widgets of one page card. */
    private static final class PageRow {
        LinearLayout box;
        CheckBox on, alt;
        EditText name, url;
        ToggleButton[] days;
    }

    /** The widgets of one on-window card; start/end are minutes of the day. */
    private static final class WindowRow {
        LinearLayout box;
        Button from, to;
        int start, end;
        ToggleButton[] days;
    }

    private LinearLayout pageList, windowList;
    private EditText seconds;
    private RadioGroup orient;
    private CheckBox pin, dimOn;
    private EditText reportToken;
    private Button dimFromBtn, dimToBtn;
    private TextView dimLevelText;
    private SeekBar dimLevel;
    private int dimFrom, dimTo;
    private final List<PageRow> pages = new ArrayList<>();
    private final List<WindowRow> windows = new ArrayList<>();
    private float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        Config cfg = Config.load(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(16), px(16), px(16), px(24));

        col.addView(title("Sider"));
        col.addView(hint("Flueben = vises. Vælg de dage siden skal med."));
        pageList = new LinearLayout(this);
        pageList.setOrientation(LinearLayout.VERTICAL);
        col.addView(pageList);
        for (Config.Page p : cfg.pages) addPage(p);
        col.addView(button("+ Tilføj side", () -> addPage(new Config.Page("", "", true, Config.ALL_DAYS, false))));

        col.addView(title("Skift side hver"));
        LinearLayout sec = new LinearLayout(this);
        sec.setGravity(Gravity.CENTER_VERTICAL);
        seconds = new EditText(this);
        seconds.setInputType(InputType.TYPE_CLASS_NUMBER);
        seconds.setText(String.valueOf(cfg.seconds));
        seconds.setMinEms(4);
        sec.addView(seconds);
        sec.addView(text(" sekunder (du kan også stryge til siden)"));
        col.addView(sec);

        col.addView(title("Skærmen er tændt"));
        col.addView(hint("Uden for tidsrummene går skærmen sort. Tænd-knappen viser siderne i 3 minutter."));
        windowList = new LinearLayout(this);
        windowList.setOrientation(LinearLayout.VERTICAL);
        col.addView(windowList);
        for (int[] w : cfg.windows) addWindow(w[0], w[1], w[2]);
        col.addView(button("+ Tilføj tidsrum", () -> addWindow(8 * 60, 16 * 60, Config.ALL_DAYS)));

        col.addView(title("Skærmlys"));
        dimFrom = cfg.dimFrom; dimTo = cfg.dimTo;
        LinearLayout dim = card();
        dimOn = new CheckBox(this);
        dimOn.setText("Dæmp skærmen om aftenen og natten");
        dimOn.setChecked(cfg.dimOn);
        dim.addView(dimOn);
        LinearLayout dimTimes = new LinearLayout(this);
        dimTimes.setGravity(Gravity.CENTER_VERTICAL);
        dimFromBtn = button(hhmm(dimFrom), () -> pickMinutes(dimFrom, v -> { dimFrom = v; dimFromBtn.setText(hhmm(v)); }));
        dimToBtn = button(hhmm(dimTo), () -> pickMinutes(dimTo, v -> { dimTo = v; dimToBtn.setText(hhmm(v)); }));
        dimTimes.addView(dimFromBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        dimTimes.addView(text("  –  "));
        dimTimes.addView(dimToBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        dim.addView(dimTimes);
        dimLevelText = text("");
        dim.addView(dimLevelText);
        dimLevel = new SeekBar(this);
        dimLevel.setMax(95);
        dimLevel.setProgress(cfg.dimLevel - 5);
        dimLevelText.setText("Lysstyrke når dæmpet: " + cfg.dimLevel + " %");
        dimLevel.setOnSeekBarChangeListener(new LevelListener(dimLevelText));
        dim.addView(dimLevel);
        col.addView(dim);

        col.addView(title("Skærmretning"));
        orient = new RadioGroup(this);
        String[] names = { "Følg tabletten", "Liggende (anbefalet)", "Stående" };
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setId(100 + i);
            orient.addView(rb);
        }
        orient.check(100 + cfg.orientation);
        col.addView(orient);

        col.addView(title("Fastlås"));
        pin = new CheckBox(this);
        pin.setText("Lås appen fast (skærmfastgørelse)");
        pin.setChecked(cfg.pin);
        col.addView(pin);
        col.addView(hint("Slå først 'Skærmfastgørelse' til i Android-indstillinger → Sikkerhed. Afslut ved at holde Tilbage og Oversigt inde samtidig."));

        col.addView(title("Opgave-feedback"));
        reportToken = new EditText(this);
        reportToken.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        reportToken.setHint("GitHub-token (kun Issues: write)");
        reportToken.setText(cfg.reportToken);
        col.addView(reportToken);
        col.addView(hint("Børnenes afkrydsning sendes som en kommentar på en opgave i GitHub. Lav et fine-grained token kun til repoet life-automations med Issues: Read and write. Uden token venter afkrydsningerne på tabletten."));

        col.addView(title("Opdatering"));
        Button upd = button("Version " + Updater.installed(this) + " – søg efter opdatering", null);
        upd.setOnClickListener(v -> {
            upd.setEnabled(false);
            upd.setText("Søger…");
            new Thread(() -> {
                String msg = Updater.run(getApplicationContext());
                runOnUiThread(() -> { upd.setText(msg); upd.setEnabled(true); });
            }).start();
        });
        col.addView(upd);

        Button save = button("Gem og start", () -> { save(); finish(); });
        save.setTextSize(18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(56));
        lp.topMargin = px(24);
        save.setLayoutParams(lp);
        col.addView(save);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }

    private int px(int v) { return (int) (v * dp); }

    private TextView text(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        return v;
    }

    private TextView title(String t) {
        TextView v = text(t);
        v.setTextSize(20);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(0, px(24), 0, px(4));
        return v;
    }

    private TextView hint(String t) {
        TextView v = text(t);
        v.setTextColor(Color.GRAY);
        v.setPadding(0, 0, 0, px(8));
        return v;
    }

    private Button button(String t, Runnable r) {
        Button bt = new Button(this);
        bt.setText(t);
        if (r != null) bt.setOnClickListener(v -> r.run());
        return bt;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(12), px(8), px(12), px(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x22FFFFFF);
        bg.setCornerRadius(px(12));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(10);
        c.setLayoutParams(lp);
        return c;
    }

    /** Seven equal-width day buttons, plus "Hverdage / Weekend / Alle" shortcuts. */
    private ToggleButton[] dayChips(LinearLayout parent, int mask) {
        LinearLayout row = new LinearLayout(this);
        ToggleButton[] chips = new ToggleButton[7];
        for (int i = 0; i < 7; i++) {
            ToggleButton t = new ToggleButton(this);
            t.setTextOn(DAYS[i]); t.setTextOff(DAYS[i]); t.setText(DAYS[i]);
            t.setTextSize(13);
            t.setMinWidth(0); t.setMinimumWidth(0);
            t.setPadding(0, 0, 0, 0);
            t.setChecked((mask & (1 << i)) != 0);
            row.addView(t, new LinearLayout.LayoutParams(0, px(44), 1f));
            chips[i] = t;
        }
        parent.addView(row);
        LinearLayout quick = new LinearLayout(this);
        quick.addView(small("Hverdage", () -> setDays(chips, Config.WEEKDAYS)));
        quick.addView(small("Weekend", () -> setDays(chips, Config.WEEKEND)));
        quick.addView(small("Alle", () -> setDays(chips, Config.ALL_DAYS)));
        parent.addView(quick);
        return chips;
    }

    private Button small(String t, Runnable r) {
        Button bt = button(t, r);
        bt.setTextSize(12);
        bt.setMinHeight(0); bt.setMinimumHeight(0);
        bt.setMinWidth(0); bt.setMinimumWidth(0);
        return bt;
    }

    private static void setDays(ToggleButton[] chips, int mask) {
        for (int i = 0; i < 7; i++) chips[i].setChecked((mask & (1 << i)) != 0);
    }

    private static int mask(ToggleButton[] chips) {
        int m = 0;
        for (int i = 0; i < 7; i++) if (chips[i].isChecked()) m |= 1 << i;
        return m;
    }

    private void addPage(Config.Page p) {
        PageRow r = new PageRow();
        r.box = card();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        r.on = new CheckBox(this);
        r.on.setChecked(p.on);
        r.name = new EditText(this);
        r.name.setHint("Navn");
        r.name.setText(p.name);
        r.name.setSingleLine(true);
        Button del = small("✕", () -> { });
        top.addView(r.on);
        top.addView(r.name, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(del);
        r.box.addView(top);

        r.url = new EditText(this);
        r.url.setHint("https://claude.ai/artifact/…");
        r.url.setText(p.url);
        r.url.setSingleLine(true);
        r.url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        r.box.addView(r.url);

        r.days = dayChips(r.box, p.days);

        r.alt = new CheckBox(this);
        r.alt.setText("Lør–man kun hver anden uge (Alex' weekender)");
        r.alt.setChecked(p.alt);
        r.box.addView(r.alt);

        del.setOnClickListener(v -> { pageList.removeView(r.box); pages.remove(r); });
        pages.add(r);
        pageList.addView(r.box);
    }

    private static String hhmm(int m) { return String.format("%02d:%02d", m / 60, m % 60); }

    private void addWindow(int start, int end, int days) {
        WindowRow r = new WindowRow();
        r.start = start; r.end = end;
        r.box = card();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        r.from = button(hhmm(start), () -> pickTime(r, true));
        r.to = button(hhmm(end), () -> pickTime(r, false));
        Button del = small("✕", () -> { });
        top.addView(r.from, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(text("  –  "));
        top.addView(r.to, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(del);
        r.box.addView(top);

        r.days = dayChips(r.box, days);

        del.setOnClickListener(v -> { windowList.removeView(r.box); windows.remove(r); });
        windows.add(r);
        windowList.addView(r.box);
    }

    interface MinuteCallback { void on(int minuteOfDay); }

    private void pickMinutes(int cur, MinuteCallback cb) {
        new TimePickerDialog(this, (view, h, m) -> cb.on(h * 60 + m), cur / 60, cur % 60, true).show();
    }

    private void pickTime(WindowRow r, boolean isStart) {
        pickMinutes(isStart ? r.start : r.end, v -> {
            if (isStart) { r.start = v; r.from.setText(hhmm(v)); }
            else { r.end = v; r.to.setText(hhmm(v)); }
        });
    }

    /** Updates the "NN %" label while the slider moves. */
    private static final class LevelListener implements SeekBar.OnSeekBarChangeListener {
        private final TextView label;
        LevelListener(TextView label) { this.label = label; }
        @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            label.setText("Lysstyrke når dæmpet: " + (progress + 5) + " %");
        }
        @Override public void onStartTrackingTouch(SeekBar bar) { }
        @Override public void onStopTrackingTouch(SeekBar bar) { }
    }

    private void save() {
        Config cfg = new Config();
        for (PageRow r : pages) {
            String u = r.url.getText().toString().trim();
            if (u.isEmpty()) continue;
            cfg.pages.add(new Config.Page(r.name.getText().toString().trim(), u, r.on.isChecked(), mask(r.days), r.alt.isChecked()));
        }
        try { cfg.seconds = Math.max(5, Math.min(3600, Integer.parseInt(seconds.getText().toString().trim()))); }
        catch (NumberFormatException e) { cfg.seconds = 60; }
        for (WindowRow r : windows) {
            int m = mask(r.days);
            if (m != 0) cfg.windows.add(new int[] { r.start, r.end, m });
        }
        cfg.orientation = Math.max(0, Math.min(2, orient.getCheckedRadioButtonId() - 100));
        cfg.pin = pin.isChecked();
        cfg.reportToken = reportToken.getText().toString().trim();
        cfg.dimOn = dimOn.isChecked();
        cfg.dimFrom = dimFrom; cfg.dimTo = dimTo; cfg.dimLevel = dimLevel.getProgress() + 5;
        cfg.save(this);
    }
}
