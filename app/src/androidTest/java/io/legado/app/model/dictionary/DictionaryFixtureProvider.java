package io.legado.app.model.dictionary;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 只读提供测试 APK 自身的夹具，独立进程不依赖目标 APK 的 Kotlin 类。 */
public final class DictionaryFixtureProvider extends DocumentsProvider {
    private static final String[] COLUMNS = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_FLAGS
    };

    private File directory() {
        File root = new File(getContext().getCacheDir(), "dictionary-provider");
        root.mkdirs();
        try { return root.getCanonicalFile(); }
        catch (IOException error) { throw new IllegalStateException(error); }
    }

    @Override public boolean onCreate() {
        File folder = new File(directory(), "collection");
        folder.mkdirs();
        try (ZipInputStream input = new ZipInputStream(getContext().getAssets().open("dictionary-fixtures.zip"))) {
            ZipEntry entry;
            byte[] bytes = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (!Arrays.asList("sample.mdx", "sample.mdd", "optional.dsl").contains(name)) continue;
                try (FileOutputStream output = new FileOutputStream(new File(folder, name))) {
                    int count;
                    while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
                }
            }
        } catch (IOException error) { throw new IllegalStateException(error); }
        return true;
    }

    private File file(String id) throws FileNotFoundException {
        if ("root".equals(id)) return directory();
        try {
            File file = new File(directory(), id).getCanonicalFile();
            if (!file.getPath().startsWith(directory().getCanonicalPath() + File.separator)) throw new FileNotFoundException(id);
            return file;
        } catch (IOException error) { throw new FileNotFoundException(id); }
    }

    @Override public Cursor queryRoots(String[] projection) {
        String[] names = projection != null ? projection : new String[] {
            DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS
        };
        MatrixCursor result = new MatrixCursor(names);
        Object[] row = new Object[names.length];
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(DocumentsContract.Root.COLUMN_ROOT_ID) || names[i].equals(DocumentsContract.Root.COLUMN_DOCUMENT_ID)) row[i] = "root";
            else if (names[i].equals(DocumentsContract.Root.COLUMN_TITLE)) row[i] = "Dictionary fixtures";
            else if (names[i].equals(DocumentsContract.Root.COLUMN_FLAGS)) row[i] = DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD;
        }
        result.addRow(row);
        return result;
    }

    private void add(MatrixCursor result, File file) {
        Object[] row = new Object[result.getColumnCount()];
        String id = file.equals(directory()) ? "root" : file.getPath().substring(directory().getPath().length() + 1);
        String[] names = result.getColumnNames();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(DocumentsContract.Document.COLUMN_DOCUMENT_ID)) row[i] = id;
            else if (names[i].equals(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) row[i] = file.getName();
            else if (names[i].equals(DocumentsContract.Document.COLUMN_MIME_TYPE)) row[i] = file.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream";
            else if (names[i].equals(DocumentsContract.Document.COLUMN_SIZE)) row[i] = file.length();
            else if (names[i].equals(DocumentsContract.Document.COLUMN_FLAGS)) row[i] = 0;
        }
        result.addRow(row);
    }

    @Override public Cursor queryDocument(String id, String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : COLUMNS);
        add(result, file(id));
        return result;
    }

    @Override public Cursor queryChildDocuments(String id, String[] projection, String order) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : COLUMNS);
        File[] files = file(id).listFiles();
        if (files != null) for (File child : files) add(result, child);
        return result;
    }

    @Override public boolean isChildDocument(String parent, String child) {
        try {
            return file(child).getPath().startsWith(file(parent).getPath() + File.separator);
        } catch (FileNotFoundException error) { return false; }
    }

    @Override public String getDocumentType(String id) throws FileNotFoundException {
        return file(id).isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream";
    }

    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
