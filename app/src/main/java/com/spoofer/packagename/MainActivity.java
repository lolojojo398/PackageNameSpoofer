package com.spoofer.packagename;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.view.Gravity;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(50, 50, 50, 50);
        layout.setBackgroundColor(Color.parseColor("#1a1a2e"));

        TextView title = new TextView(this);
        title.setText("小米音乐去广告");
        title.setTextSize(28);
        title.setTextColor(Color.parseColor("#e94560"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 30);

        TextView status = new TextView(this);
        status.setText("模块已激活");
        status.setTextSize(20);
        status.setTextColor(Color.parseColor("#00ff88"));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 20, 0, 20);

        TextView desc = new TextView(this);
        desc.setText("功能说明：\n\n"
            + "  跳过广告倒计时，立即领取金币\n"
            + "  无需手动修改系统时间\n\n"
            + "使用方法：\n"
            + "1. 在LSPosed中启用本模块\n"
            + "2. 勾选 com.miui.player\n"
            + "3. 重启小米音乐\n"
            + "4. 进入金币中心\n"
            + "5. 点击「看广告领金币」\n"
            + "6. 广告加载后立即获得金币");
        desc.setTextSize(16);
        desc.setTextColor(Color.parseColor("#aaaaaa"));
        desc.setGravity(Gravity.CENTER);

        layout.addView(title);
        layout.addView(status);
        layout.addView(desc);

        setContentView(layout);
    }
}
