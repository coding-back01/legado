package io.legado.app.release;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * 在 test APK 自身 UID 下生成离线 fixture URI，再只读授权给目标 Debug 应用。
 */
public final class ReleaseSmokeFixtureDispatchActivity extends Activity {

    private static final String TARGET_PACKAGE = "io.legado.app.debug";
    private static final String TARGET_ACTIVITY =
            "io.legado.app.ui.association.FileAssociationActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getPackageName().equals(TARGET_PACKAGE + ".test")) {
            throw new IllegalStateException("发布烟测只能运行 Debug 测试 APK");
        }
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(getPackageName() + ".releaseSmokeFixtureProvider")
                .appendPath(ReleaseSmokeFixtureProvider.SOURCE_ASSET)
                .build();
        Intent target = new Intent()
                .setClassName(TARGET_PACKAGE, TARGET_ACTIVITY)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(target);
        finish();
    }
}
