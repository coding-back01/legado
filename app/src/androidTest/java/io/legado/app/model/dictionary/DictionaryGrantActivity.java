package io.legado.app.model.dictionary;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** 测试提供者自身 UID 模拟系统选择器向 Debug 应用授予只读目录。 */
public final class DictionaryGrantActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String name = getPackageName();
        Uri uri = DocumentsContract.buildTreeDocumentUri(name + ".dictionaryFixture", "collection");
        android.database.Cursor cursor = getContentResolver().query(
            DocumentsContract.buildDocumentUriUsingTree(uri, "collection"), null, null, null, null);
        if (cursor != null) cursor.close();
        grantUriPermission(name.substring(0, name.length() - 5), uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        finish();
    }
}
