package io.legado.app.release;

import android.content.ContentProvider;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * 只读提供 test APK 内的离线书源 fixture，不访问目标应用或用户文件。
 */
public final class ReleaseSmokeFixtureProvider extends ContentProvider {

    public static final String SOURCE_ASSET = "release-smoke-source.json";
    private File fixtureFile;

    @Override
    public boolean onCreate() {
        fixtureFile = new File(context().getCacheDir(), SOURCE_ASSET);
        try (
                InputStream input = context().getAssets().open(SOURCE_ASSET);
                FileOutputStream output = new FileOutputStream(fixtureFile)
        ) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        } catch (IOException error) {
            throw new IllegalStateException("无法准备发布烟测 fixture", error);
        }
        return true;
    }

    @Override
    public String getType(Uri uri) {
        requireFixture(uri);
        return "application/json";
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        requireFixture(uri);
        String[] columns = projection != null
                ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        Object[] row = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) {
                row[index] = SOURCE_ASSET;
            } else if (OpenableColumns.SIZE.equals(columns[index])) {
                row[index] = fixture().length();
            }
        }
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("发布烟测 fixture 只允许只读访问");
        }
        requireFixture(uri);
        return ParcelFileDescriptor.open(fixture(), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("发布烟测 fixture 只允许只读访问");
    }

    @Override
    public int update(
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs
    ) {
        throw new UnsupportedOperationException("发布烟测 fixture 只允许只读访问");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("发布烟测 fixture 只允许只读访问");
    }

    private File fixture() {
        if (fixtureFile == null || !fixtureFile.isFile()) {
            throw new IllegalStateException("发布烟测 fixture 尚未准备完成");
        }
        return fixtureFile;
    }

    private void requireFixture(Uri uri) {
        if (!SOURCE_ASSET.equals(uri.getLastPathSegment())) {
            throw new IllegalArgumentException("未知发布烟测 fixture");
        }
    }

    private Context context() {
        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("发布烟测 Provider 尚未初始化");
        }
        return context;
    }
}
