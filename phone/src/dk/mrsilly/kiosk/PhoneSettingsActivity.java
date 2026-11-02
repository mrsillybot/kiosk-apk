package dk.mrsilly.kiosk;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Phone settings: the GitHub token for the lists repo (same one as on the tablet) and a button to look for a new version. */
public class PhoneSettingsActivity extends Activity {
    private EditText token;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = getSharedPreferences("kiosk", Context.MODE_PRIVATE);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);
        TextView h = new TextView(this);
        h.setText("GitHub-token til listerne");
        h.setTextSize(18);
        col.addView(h);
        token = new EditText(this);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setHint("github_pat_… (kun life-lists, Contents)");
        token.setText(p.getString("reportToken", ""));
        col.addView(token);
        TextView hint = new TextView(this);
        hint.setText("Samme token som på køleskabet: fine-grained, kun repoet life-lists, Contents: Read and write, uden udløb.");
        hint.setPadding(0, pad / 2, 0, pad);
        col.addView(hint);
        Button save = new Button(this);
        save.setText("Gem");
        save.setOnClickListener(v -> { p.edit().putString("reportToken", token.getText().toString().trim()).apply(); finish(); });
        col.addView(save);
        Button upd = new Button(this);
        upd.setText("Version " + Updater.installed(this) + " – søg efter opdatering");
        upd.setOnClickListener(v -> {
            upd.setEnabled(false);
            upd.setText("Søger…");
            new Thread(() -> {
                String msg = Updater.run(getApplicationContext());
                runOnUiThread(() -> { upd.setText(msg); upd.setEnabled(true); });
            }).start();
        });
        col.addView(upd);
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }
}
