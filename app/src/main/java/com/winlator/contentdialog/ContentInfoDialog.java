package com.winlator.contentdialog;

import android.content.Context;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.contents.ContentProfile;

public class ContentInfoDialog extends ContentDialog {
    public ContentInfoDialog(Context context, ContentProfile profile) {
        super(context, R.layout.content_info_dialog);
        setIcon(R.drawable.icon_about);
        setTitle(R.string.content_info);

        TextView tvType = findViewById(R.id.TVType);
        TextView tvVersion = findViewById(R.id.TVVersion);
        TextView tvVersionCode = findViewById(R.id.TVVersionCode);
        TextView tvDescription = findViewById(R.id.TVDesc);
        TextView tvFiles = findViewById(R.id.TVFiles);

        tvType.setText(profile.type.toString());
        tvVersion.setText(profile.verName);
        tvVersionCode.setText(String.valueOf(profile.verCode));
        tvDescription.setText(profile.desc);

        StringBuilder stb = new StringBuilder();
        for (String str : profile.fileList)
            stb.append(str).append('\n');
        tvFiles.setText(stb.toString());
    }
}
