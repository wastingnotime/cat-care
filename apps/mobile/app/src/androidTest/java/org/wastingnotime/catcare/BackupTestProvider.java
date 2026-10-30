package org.wastingnotime.catcare;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Runs in the test APK's own process, without the target APK's Kotlin runtime. */
public class BackupTestProvider extends DocumentsProvider {
    private File root() { File root = new File(getContext().getCacheDir(), "backup-documents"); root.mkdirs(); return root; }
    private File file(String id) {
        if (id.equals("root")) return root();
        if (id.isEmpty() || id.contains("/") || id.equals(".") || id.equals("..")) throw new IllegalArgumentException();
        return new File(root(), id);
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor queryRoots(String[] projection) { return new MatrixCursor(projection == null ? new String[]{"root_id"} : projection); }
    @Override public Cursor queryDocument(String id, String[] projection) { return rows(new File[]{file(id)}, projection); }
    @Override public Cursor queryChildDocuments(String id, String[] projection, String order) {
        File[] files = file(id).listFiles(); return rows(files == null ? new File[0] : files, projection);
    }
    private Cursor rows(File[] files, String[] projection) {
        String[] fields = projection == null ? new String[]{Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED} : projection;
        MatrixCursor cursor = new MatrixCursor(fields);
        for (File file : files) {
            Object[] row = new Object[fields.length];
            for (int i = 0; i < fields.length; i++) {
                switch (fields[i]) {
                    case Document.COLUMN_DOCUMENT_ID: row[i] = file.equals(root()) ? "root" : file.getName(); break;
                    case Document.COLUMN_DISPLAY_NAME: row[i] = file.getName(); break;
                    case Document.COLUMN_MIME_TYPE: row[i] = file.isDirectory() ? Document.MIME_TYPE_DIR : "application/json"; break;
                    case Document.COLUMN_FLAGS: row[i] = Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_RENAME | (file.isDirectory() ? Document.FLAG_DIR_SUPPORTS_CREATE : 0); break;
                    case Document.COLUMN_SIZE: row[i] = file.length(); break;
                    case Document.COLUMN_LAST_MODIFIED: row[i] = file.lastModified(); break;
                }
            }
            cursor.addRow(row);
        }
        return cursor;
    }
    @Override public String createDocument(String parent, String type, String name) throws FileNotFoundException {
        if (!parent.equals("root")) throw new IllegalArgumentException();
        try { if (!file(name).createNewFile()) throw new IOException(); } catch (IOException error) { throw new FileNotFoundException(); }
        return name;
    }
    @Override public String renameDocument(String id, String name) throws FileNotFoundException {
        if (new File(root(), "reject-rename").exists() || !file(id).renameTo(file(name))) throw new FileNotFoundException("Injected rename failure");
        return name;
    }
    @Override public void deleteDocument(String id) throws FileNotFoundException {
        if (id.equals("root") || !file(id).delete()) throw new FileNotFoundException();
    }
    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.parseMode(mode));
    }
    @Override public boolean isChildDocument(String parent, String id) { return parent.equals("root") && !id.equals("root"); }
}
