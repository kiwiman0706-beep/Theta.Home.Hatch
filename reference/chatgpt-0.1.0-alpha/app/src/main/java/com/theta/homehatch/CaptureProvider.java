package com.theta.homehatch;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.*;

/** Only the app-owned capture directory can be shared through this provider. */
public class CaptureProvider extends ContentProvider {
    public static File root(Context context) { File f = new File(context.getFilesDir(), "Capture"); f.mkdirs(); return f; }
    public static Uri uri(Context c, File f) {
        String relative = root(c).toPath().relativize(f.toPath()).toString().replace('\\','/');
        return new Uri.Builder().scheme("content").authority(c.getPackageName()+".files").path(relative).build();
    }
    private File file(Uri uri) throws FileNotFoundException {
        try {
            File base=root(getContext()).getCanonicalFile();
            String path=uri.getPath();
            if(path==null)throw new FileNotFoundException("Invalid path");
            File f=new File(base,path.replaceFirst("^/","")).getCanonicalFile();
            if(!f.toPath().startsWith(base.toPath()) || f.equals(base)) throw new FileNotFoundException("Invalid path");
            return f;
        } catch(IOException e) { throw new FileNotFoundException("Invalid path"); }
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri u){String ext=MimeTypeMap.getFileExtensionFromUrl(u.toString()).toLowerCase(java.util.Locale.ROOT);String type=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);return type==null?"application/octet-stream":type;}
    @Override public ParcelFileDescriptor openFile(Uri u,String mode) throws FileNotFoundException {File f=file(u);return ParcelFileDescriptor.open(f,ParcelFileDescriptor.parseMode(mode));}
    @Override public Cursor query(Uri u,String[] projection,String selection,String[] args,String order){
        try {File f=file(u);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?f.getName():OpenableColumns.SIZE.equals(cols[i])?f.length():null;c.addRow(row);return c;}catch(Exception e){return null;}
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
