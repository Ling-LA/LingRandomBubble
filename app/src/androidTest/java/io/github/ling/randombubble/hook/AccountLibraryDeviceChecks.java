package io.github.ling.randombubble.hook;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.util.AtomicFile;
import io.github.ling.randombubble.core.BubbleSpec;
import io.github.ling.randombubble.store.Config;
import io.github.ling.randombubble.store.JsonCodec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Production AtomicFile/library checks, confined to a fresh temporary synthetic account. */
public final class AccountLibraryDeviceChecks {
    private static final String OWNER="12345678";
    private AccountLibraryDeviceChecks() {}
    private static final class FixtureContext extends ContextWrapper {
        private final File files;
        int filesRequests;
        FixtureContext(Context context,File files) {super(context);this.files=files;}
        @Override public Context getApplicationContext() {return this;}
        @Override public File getFilesDir() {filesRequests++;return files;}
        @Override public File getExternalFilesDir(String type) {return files;}
        @Override public ApplicationInfo getApplicationInfo() {
            ApplicationInfo info=new ApplicationInfo();info.dataDir=files.getAbsolutePath();return info;
        }
    }
    private static void check(Consumer<String> passed,String name,boolean value) {
        if(!value)throw new AssertionError(name);passed.accept(name);
    }
    private static AccountLibrary library(FixtureContext context) throws Exception {
        AccountLibrary library=new AccountLibrary(context);
        Field account=AccountLibrary.class.getDeclaredField("account");account.setAccessible(true);account.set(library,OWNER);
        return library;
    }
    private static String saved(AccountLibrary library) throws Exception {
        Field field=AccountLibrary.class.getDeclaredField("savedJson");field.setAccessible(true);return (String)field.get(library);
    }
    private static File file(File fixture) throws Exception {
        Method name=AccountLibrary.class.getDeclaredMethod("fileName",String.class);name.setAccessible(true);
        return new File(new File(fixture,"ling-bubble"),(String)name.invoke(null,OWNER));
    }
    private static boolean load(AccountLibrary library) throws Exception {
        Method load=AccountLibrary.class.getDeclaredMethod("load",String.class);load.setAccessible(true);return (Boolean)load.invoke(library,OWNER);
    }
    private static String read(File file) throws Exception {
        try(FileInputStream input=new FileInputStream(file);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
            return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private static void write(File file,String text) throws Exception {
        try(FileOutputStream output=new FileOutputStream(file)) {output.write(text.getBytes(StandardCharsets.UTF_8));output.getFD().sync();}
    }
    private static long mark(File file) throws Exception {
        if(!file.setLastModified(1680000000000L))throw new IllegalStateException("Cannot mark isolated library write time");
        return file.lastModified();
    }
    private static void deleteWithin(File file,File fixture) throws Exception {
        String root=fixture.getCanonicalPath();String path=file.getCanonicalPath();
        if(!path.equals(root) && !path.startsWith(root+File.separator))throw new IllegalStateException("Fixture deletion escaped its directory");
        if(file.isDirectory()) {
            File[] children=file.listFiles();if(children==null)throw new IllegalStateException("Cannot list fixture directory");
            for(File child:children)deleteWithin(child,fixture);
        }
        if(file.exists() && !file.delete())throw new IllegalStateException("Cannot remove isolated library fixture");
    }
    public static void run(Context context,Consumer<String> passed) throws Exception {
        File cache=context.getCacheDir().getCanonicalFile();
        File fixture=new File(cache,"LingBubble-account-library-device-test-"+UUID.randomUUID()).getCanonicalFile();
        if(!cache.equals(fixture.getParentFile()) || !fixture.mkdir())throw new IllegalStateException("Cannot create isolated library fixture");
        try {
            FixtureContext fake=new FixtureContext(context,fixture);
            AccountLibrary library=library(fake);
            Config initial=JsonCodec.config(JsonCodec.defaults().toString());
            BubbleSpec a=new BubbleSpec(17,17,0L,101,201,null,0),b=new BubbleSpec(17,17,0L,102,202,null,0);
            fake.filesRequests=0;
            Config harvested=library.addAll(Arrays.asList(a,a,b,b),initial);
            File target=file(fixture);
            check(passed,"account library batch deduplicates and saves one document",library.document().getJSONArray("bubbles").length()==2 && target.isFile() && fake.filesRequests==1 && library.json().equals(saved(library)));
            check(passed,"account library harvest neither selects nor enables",harvested.selected.isEmpty() && !harvested.enabled && !harvested.collect && !library.document().optBoolean("automatic") && !library.document().optBoolean("perMessage"));
            String baseline=library.json();long modified=mark(target);fake.filesRequests=0;
            Config duplicate=library.addAll(Arrays.asList(a,b,a,null),harvested);
            check(passed,"duplicate account harvest does not rewrite its file",duplicate==harvested && baseline.equals(library.json()) && baseline.equals(saved(library)) && target.lastModified()==modified && fake.filesRequests==0);
            library.replaceJson(baseline);
            check(passed,"identical account replacement skips disk write",target.lastModified()==modified && baseline.equals(saved(library)) && fake.filesRequests==0);
            AccountLibrary restarted=library(fake);
            check(passed,"account library reload preserves synthetic-account metadata",load(restarted) && restarted.document().getJSONArray("bubbles").length()==2 && restarted.configOrNull().selected.isEmpty() && baseline.equals(saved(restarted)));
            AtomicFile atomic=new AtomicFile(target);
            FileOutputStream interrupted=atomic.startWrite();
            try {interrupted.write("{interrupted".getBytes(StandardCharsets.UTF_8));interrupted.getFD().sync();} finally {interrupted.close();}
            AccountLibrary recovered=library(fake);
            check(passed,"atomic library load recovers an interrupted replacement",load(recovered) && baseline.equals(read(target)) && recovered.document().getJSONArray("bubbles").length()==2);
            // Android retains support for the legacy .bak recovery path during AtomicFile reads.
            File backup=new File(target.getPath()+".bak");write(backup,baseline);write(target,"{broken");
            AccountLibrary legacyRecovered=library(fake);
            check(passed,"atomic library load restores a valid backup over corrupt base",load(legacyRecovered) && !backup.exists() && baseline.equals(read(target)) && legacyRecovered.configOrNull().selected.isEmpty() && !legacyRecovered.configOrNull().enabled);
        } finally {
            deleteWithin(fixture,fixture);
        }
        check(passed,"account library fixture removes only its temporary directory",!fixture.exists());
    }
}
