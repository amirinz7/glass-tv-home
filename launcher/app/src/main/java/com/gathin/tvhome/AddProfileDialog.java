package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * TV Dialog for creating a new user profile.
 */
public class AddProfileDialog {

    public interface OnProfileAddedListener {
        void onProfileAdded(ProfileManager.UserProfile profile);
    }

    public static void show(Context context, OnProfileAddedListener listener) {
        if (context == null) return;
        float density = context.getResources().getDisplayMetrics().density;
        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_glass_dialog);
        root.setPadding((int) (28 * density), (int) (24 * density), (int) (28 * density), (int) (24 * density));

        // Header
        TextView tvTitle = new TextView(context);
        tvTitle.setText("+ Add New Profile");
        tvTitle.setTextSize(21);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(context.getResources().getFont(R.font.instrument_sans), android.graphics.Typeface.BOLD);
        root.addView(tvTitle);

        TextView tvSub = new TextView(context);
        tvSub.setText("Create a profile for a family member or child mode");
        tvSub.setTextSize(13);
        tvSub.setTextColor(0xFFA89F91);
        tvSub.setPadding(0, (int) (3 * density), 0, (int) (18 * density));
        root.addView(tvSub);

        // Name input
        TextView tvNameLabel = new TextView(context);
        tvNameLabel.setText("PROFILE NAME");
        tvNameLabel.setTextSize(11.5f);
        tvNameLabel.setTextColor(0xFFFFB347);
        tvNameLabel.setTypeface(context.getResources().getFont(R.font.instrument_sans), android.graphics.Typeface.BOLD);
        root.addView(tvNameLabel);

        EditText etName = new EditText(context);
        etName.setHint("Enter profile name...");
        etName.setHintTextColor(0xFF8A7E6E);
        etName.setTextColor(0xFFFFFFFF);
        etName.setTextSize(15);
        etName.setSingleLine(true);
        etName.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));

        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0x22FFFFFF);
        etBg.setCornerRadius(12 * density);
        etBg.setStroke((int) (1.2f * density), 0x33FFFFFF);
        etName.setBackground(etBg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (6 * density);
        lp.bottomMargin = (int) (14 * density);
        root.addView(etName, lp);

        // Child mode checkbox
        CheckBox cbChild = new CheckBox(context);
        cbChild.setText("Make this a Child profile (blocks adult apps)");
        cbChild.setTextColor(0xFFF1E7D3);
        cbChild.setTextSize(13.5f);
        cbChild.setPadding((int) (6 * density), 0, 0, (int) (18 * density));
        root.addView(cbChild);

        // Action Buttons Row
        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.END);

        Button btnCancel = new Button(context, null, android.R.attr.borderlessButtonStyle);
        btnCancel.setText("Cancel");
        btnCancel.setTextColor(0xFFA89F91);
        actionsRow.addView(btnCancel);

        Button btnAdd = new Button(context);
        btnAdd.setText("Add Profile");
        btnAdd.setTextColor(0xFFFFFFFF);
        btnAdd.setTextSize(13.5f);
        btnAdd.setBackgroundResource(R.drawable.bg_dialog_btn);
        btnAdd.setPadding((int) (22 * density), 0, (int) (22 * density), 0);
        actionsRow.addView(btnAdd, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (42 * density)));

        root.addView(actionsRow);

        builder.setView(root);
        AlertDialog dialog = builder.create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAdd.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(context, "Please enter a profile name", Toast.LENGTH_SHORT).show();
                return;
            }
            ProfileManager.UserProfile p = ProfileManager.addProfile(context, name, cbChild.isChecked());
            Toast.makeText(context, "Profile " + name + " added!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (listener != null) listener.onProfileAdded(p);
        });

        dialog.show(); GlassPage.glassDialog(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(Math.round(560 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
